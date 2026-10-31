package com.sumit.trustforge.ai;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public final class BharatCodeGateway implements AIModelGateway {
    private static final int MAX_RESPONSE_BYTES = 131072;
    private final HttpClient client;
    private final Semaphore permits = new Semaphore(2);
    private final ObjectMapper json;
    private final JdbcTemplate db;
    private final MeterRegistry metrics;
    private final String key;
    private final String model;
    private final URI endpoint;
    private final int timeout;
    private final int dailyQuota;

    @org.springframework.beans.factory.annotation.Autowired
    public BharatCodeGateway(ObjectMapper json, JdbcTemplate db, MeterRegistry metrics,
            @Value("${trustforge.ai.api-key:}") String key,
            @Value("${trustforge.ai.base-url:https://bharatcode.ai/api/model/v1}") String base,
            @Value("${trustforge.ai.model:qwen-3.8-27b}") String model,
            @Value("${trustforge.ai.timeout-seconds:45}") int timeout,
            @Value("${trustforge.ai.daily-request-limit:200}") int dailyQuota) {
        this(json, db, metrics, key, base, model, timeout, dailyQuota, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    BharatCodeGateway(ObjectMapper json, JdbcTemplate db, MeterRegistry metrics, String key, String base, String model,
            int timeout, int dailyQuota, HttpClient client) {
        this.client = client;
        this.json = json;
        this.db = db;
        this.metrics = metrics;
        this.key = key;
        this.model = model;
        this.timeout = Math.clamp(timeout, 5, 90);
        this.dailyQuota = Math.clamp(dailyQuota, 1, 10000);
        URI uri = URI.create(base);
        if (!"https".equals(uri.getScheme()) || !"bharatcode.ai".equals(uri.getHost()) ||
                uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null ||
                uri.getFragment() != null || !"/api/model/v1".equals(uri.getPath())) {
            throw new IllegalArgumentException("BharatCode endpoint must be the documented HTTPS provider URL");
        }
        if (!model.matches("[a-zA-Z0-9._-]{1,100}"))
            throw new IllegalArgumentException("Invalid model identifier");
        this.endpoint = URI.create(base + "/chat/completions");
    }

    @Override
    public boolean configured() {
        return !key.isBlank();
    }

    @Override public Descriptor descriptor(String requestedModel) {
        return new Descriptor("BHARATCODE",requestedModel==null?model:requestedModel,Map.of("temperature",0,"max_tokens",2500,"reasoning_effort","none","store",false,"endpoint",endpoint.toString(),"requestTimeoutSeconds",timeout,"maximumAttempts",2));
    }

    @Override
    public Response complete(Request request) {
        if (!configured())
            throw new ModelFailure("CONFIGURATION","BharatCode is not configured. Set the server BHARATCODE_API_KEY.");
        if (request.system().length() + request.input().length() > 60000)
            throw new ModelFailure("INPUT_LIMIT","Model input exceeds the configured size limit");
        boolean acquired = false;
        long start = System.nanoTime();
        Instant requestedAt=Instant.now();int retries=0;int transportAttempts=0;String failureClass="NONE";int httpStatus=0;
        try {
            acquired = permits.tryAcquire(1, TimeUnit.SECONDS);
            if (!acquired)
                throw new ModelFailure("BUSY","Provider concurrency limit reached; retry later");
            int reserved = db.update("""
                    INSERT INTO model_daily_usage(actor_id,usage_date,requests) VALUES (?,CURRENT_DATE,1)
                    ON CONFLICT (actor_id,usage_date) DO UPDATE SET requests=model_daily_usage.requests+1
                    WHERE model_daily_usage.requests < ?
                    """, request.actorId(), dailyQuota);
            if (reserved == 0)
                throw new ModelFailure("QUOTA","Daily model request quota reached");
            String requestedModel = request.model() == null ? model : request.model();
            if (!requestedModel.matches("[a-zA-Z0-9._-]{1,100}"))
                throw new ModelFailure("Invalid requested model identifier");
            String payload = json.writeValueAsString(Map.of("model", requestedModel, "store", false,
                    "max_tokens", 2500, "reasoning_effort", "none", "temperature", 0,
                    "messages", List.of(Map.of("role", "system", "content", request.system()),
                            Map.of("role", "user", "content", request.input()))));
            for (int attempt = 0; attempt < 2; attempt++) {
                long remaining = Duration.between(Instant.now(), request.deadline()).toMillis();
                if (remaining <= 0 || Thread.currentThread().isInterrupted())
                    throw new ModelFailure("TIMEOUT","Model request deadline reached");
                HttpRequest call = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofMillis(Math.min(timeout * 1000L, remaining)))
                        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                transportAttempts++;
                CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(call, info -> limitedBody());
                HttpResponse<byte[]> response;
                try {
                    response = pending.get(Math.min(timeout * 1000L, remaining), TimeUnit.MILLISECONDS);
                } catch (TimeoutException ex) {
                    pending.cancel(true);
                    throw new ModelFailure("TIMEOUT","Provider total response deadline exceeded");
                } catch (InterruptedException ex) {
                    pending.cancel(true);
                    throw ex;
                }
                httpStatus=response.statusCode();
                byte[] bytes = response.body();
                if (bytes.length > MAX_RESPONSE_BYTES)
                    throw new ModelFailure("Provider response exceeds size limit");
                if ((response.statusCode() == 429 || response.statusCode() == 500 || response.statusCode() == 502 || response.statusCode() == 503) && attempt == 0) {
                    long retry = retryDelay(response.headers().firstValue("Retry-After").orElse(""));
                    remaining = Duration.between(Instant.now(), request.deadline()).toMillis();
                    if (retry < 0)
                        throw new ModelFailure(
                                "Provider requested a retry delay beyond the bounded retry budget; retry later");
                    if (remaining < retry + 1000)
                        throw new ModelFailure("Provider busy beyond request deadline");
                    retries++;
                    metrics.counter("trustforge.provider.retries").increment();
                    Thread.sleep(retry);
                    continue;
                }
                if (response.statusCode() != 200) {
                    metrics.counter("trustforge.provider.errors", "kind", "http").increment();
                    throw new ModelFailure(response.statusCode()==401||response.statusCode()==403?"AUTHENTICATION":response.statusCode()==429?"RATE_LIMIT":response.statusCode()>=500?"UNAVAILABLE":"REQUEST_REJECTED",response.statusCode() == 429 ? "Provider rate limit reached"
                            : "Provider rejected request (HTTP " + response.statusCode() + ")");
                }
                JsonNode root = json.readTree(new String(bytes, StandardCharsets.UTF_8));
                JsonNode choice = root.path("choices").path(0);
                if (!"stop".equals(choice.path("finish_reason").asText()))
                    throw new ModelFailure("INCOMPLETE_OUTPUT","Provider returned incomplete output; evaluation is inconclusive");
                String content = choice.path("message").path("content").asText("");
                if (content.isBlank() || content.length() > 32000)
                    throw new ModelFailure("INVALID_RESPONSE","Provider returned empty or oversized content");
                JsonNode inputUsage=root.path("usage").path("prompt_tokens"),outputUsage=root.path("usage").path("completion_tokens");
                boolean usageAvailable=inputUsage.isIntegralNumber()&&outputUsage.isIntegralNumber()&&inputUsage.asLong(-1)>=0&&inputUsage.asLong(-1)<=1_000_000&&outputUsage.asLong(-1)>=0&&outputUsage.asLong(-1)<=1_000_000;
                long input=usageAvailable?inputUsage.asLong():0,output=usageAvailable?outputUsage.asLong():0;
                if(usageAvailable)db.update("UPDATE model_daily_usage SET input_tokens=input_tokens+?,output_tokens=output_tokens+? WHERE actor_id=? AND usage_date=CURRENT_DATE",input,output,request.actorId());
                String requestId=response.headers().firstValue("x-request-id").orElse("");
                if(!requestId.matches("[a-zA-Z0-9._:-]{0,128}"))requestId="";
                return new Response(content,input,output,requestId,new Metadata(requestedModel,requestedAt,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),retries,usageAvailable,transportAttempts));
            }
            throw new ModelFailure("Provider unavailable after bounded retry");
        } catch (ModelFailure ex) {
            failureClass=ex.classification();throw new ModelFailure(failureClass,ex.getMessage(),retries,transportAttempts);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failureClass="INTERRUPTED";throw new ModelFailure(failureClass,"Model request cancelled",retries,transportAttempts);
        } catch (Exception ex) {
            metrics.counter("trustforge.provider.errors", "kind", "network_or_format").increment();
            Throwable cause=ex instanceof java.util.concurrent.ExecutionException?ex.getCause():ex;
            failureClass=cause instanceof org.springframework.dao.DataAccessException?"PERSISTENCE":cause instanceof java.net.http.HttpTimeoutException?"TIMEOUT":cause instanceof tools.jackson.core.JacksonException?"INVALID_RESPONSE":"TRANSPORT_OR_FORMAT";
            throw new ModelFailure(failureClass,"Provider network, timeout or response format failure",retries,transportAttempts);
        } finally {
            org.slf4j.LoggerFactory.getLogger(getClass()).info("provider_call model={} requested_at={} duration_ms={} retries={} http_status={} failure={}",request.model()==null?model:request.model(),requestedAt,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),retries,httpStatus,failureClass);
            if (acquired)
                permits.release();
            metrics.timer("trustforge.provider.duration").record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    static HttpResponse.BodySubscriber<byte[]> limitedBody() {
        HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        return new HttpResponse.BodySubscriber<>() {
            private Flow.Subscription upstream;
            private long received;
            private boolean rejected;

            @Override
            public CompletionStage<byte[]> getBody() {
                return delegate.getBody();
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                upstream = subscription;
                delegate.onSubscribe(subscription);
            }

            @Override
            public void onNext(List<ByteBuffer> buffers) {
                if (rejected)
                    return;
                for (ByteBuffer buffer : buffers)
                    received += buffer.remaining();
                if (received > MAX_RESPONSE_BYTES) {
                    rejected = true;
                    upstream.cancel();
                    delegate.onError(new IOException("Provider response exceeds size limit"));
                } else
                    delegate.onNext(buffers);
            }

            @Override
            public void onError(Throwable error) {
                if (!rejected)
                    delegate.onError(error);
            }

            @Override
            public void onComplete() {
                if (!rejected)
                    delegate.onComplete();
            }
        };
    }

    static long retryDelay(String value) {
        long delay = 1000;
        if (!value.isBlank()) {
            try {
                long seconds = Long.parseLong(value);
                if (seconds > 3)
                    return -1;
                delay = Math.max(0, seconds) * 1000L;
            } catch (NumberFormatException ex) {
                try {
                    Instant requested = java.time.ZonedDateTime
                            .parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    delay = Math.max(0, Duration.between(Instant.now(), requested).toMillis());
                    if (delay > 3000)
                        return -1;
                } catch (java.time.format.DateTimeParseException invalidHeader) {
                    delay = 1000;
                }
            }
        }
        return Math.clamp(delay, 500, 3000) + ThreadLocalRandom.current().nextLong(150);
    }
}
