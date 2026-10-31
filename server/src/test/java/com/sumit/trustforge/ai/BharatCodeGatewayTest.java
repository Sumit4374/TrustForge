package com.sumit.trustforge.ai;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;

import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BharatCodeGatewayTest {
    private static final String BASE="https://bharatcode.ai/api/model/v1";
    private final JdbcTemplate db=new JdbcTemplate(){@Override public int update(String sql,Object... args){return 1;}};
    private BharatCodeGateway gateway(HttpClient client){return new BharatCodeGateway(JsonMapper.builder().build(),db,new SimpleMeterRegistry(),"test-only-not-a-real-key",BASE,"qwen-3.8-27b",5,200,client);}
    @SuppressWarnings("unchecked") private HttpResponse<byte[]> response(String json,int status) {
        HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b)->true));return response;
    }
    private AIModelGateway.Request request(){return new AIModelGateway.Request(UUID.randomUUID(),"Return JSON","Synthetic test input",Instant.now().plusSeconds(10));}
    @Test @SuppressWarnings("unchecked") void validResponseAccountsUsage()throws Exception {
        HttpClient client=mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}",200))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var result=gateway(client).complete(request());assertEquals(10,result.inputTokens());assertEquals(5,result.outputTokens());assertTrue(result.content().contains("ok"));assertEquals("qwen-3.8-27b",result.metadata().model());assertTrue(result.metadata().usageAvailable());assertEquals(0,result.metadata().retries());assertEquals(1,result.metadata().transportAttempts());
    }
    @Test @SuppressWarnings("unchecked") void incompleteOutputNeverExecutesAsSuccess()throws Exception {
        HttpClient client=mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}",200))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThrows(ModelFailure.class,()->gateway(client).complete(request()));verify(client,times(1)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test @SuppressWarnings("unchecked") void malformedAndOversizedResponsesFailClosed()throws Exception {
        for(String payload:new String[]{"{broken}","x".repeat(131073)}) {
            HttpClient client=mock(HttpClient.class);doReturn(CompletableFuture.completedFuture(response(payload,200))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
            assertThrows(ModelFailure.class,()->gateway(client).complete(request()));
        }
    }
    @Test @SuppressWarnings("unchecked") void networkAndTimeoutErrorsHaveSafeMessages()throws Exception {
        HttpClient client=mock(HttpClient.class);doReturn(CompletableFuture.failedFuture(new HttpTimeoutException("sensitive underlying network detail"))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var failure=assertThrows(ModelFailure.class,()->gateway(client).complete(request()));assertFalse(failure.getMessage().contains("sensitive"));
    }
    @Test void arbitraryEndpointCannotReachLocalMetadata() {
        assertThrows(IllegalArgumentException.class,()->new BharatCodeGateway(JsonMapper.builder().build(),db,new SimpleMeterRegistry(),"test","http://169.254.169.254","model",5,5,mock(HttpClient.class)));
    }
    @Test void missingKeyAndExpiredDeadlineFailBeforeNetwork() {
        var unconfigured=new BharatCodeGateway(JsonMapper.builder().build(),db,new SimpleMeterRegistry(),"",BASE,"qwen-3.8-27b",5,5,mock(HttpClient.class));
        assertThrows(ModelFailure.class,()->unconfigured.complete(request()));assertFalse(unconfigured.configured());
        assertThrows(ModelFailure.class,()->gateway(mock(HttpClient.class)).complete(new AIModelGateway.Request(UUID.randomUUID(),"system","input",Instant.now().minusSeconds(1))));
    }
    @Test void responseSubscriberCancelsOversizedStreamingBody() {
        var subscriber=BharatCodeGateway.limitedBody();
        java.util.concurrent.Flow.Subscription upstream=mock(java.util.concurrent.Flow.Subscription.class);
        subscriber.onSubscribe(upstream);
        subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[131073])));
        verify(upstream).cancel();
        assertThrows(java.util.concurrent.CompletionException.class,()->subscriber.getBody().toCompletableFuture().join());
    }
    @Test @SuppressWarnings("unchecked") void asynchronousFailureNeverLeaksProviderBody()throws Exception {
        HttpClient client=mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response("sensitive_provider_debug_payload",401))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var failure=assertThrows(ModelFailure.class,()->gateway(client).complete(request()));
        assertFalse(failure.getMessage().contains("sensitive_provider"));
    }

    @Test @SuppressWarnings("unchecked") void stalledBodyIsCancelledAtAbsoluteDeadline()throws Exception {
        HttpClient client=mock(HttpClient.class);
        CompletableFuture<HttpResponse<byte[]>> pending=new CompletableFuture<>();
        doReturn(pending).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var shortRequest=new AIModelGateway.Request(UUID.randomUUID(),"Synthetic system","Synthetic input",Instant.now().plusMillis(100));
        var failure=assertThrows(ModelFailure.class,()->gateway(client).complete(shortRequest));
        assertTrue(failure.getMessage().contains("deadline"));assertTrue(pending.isCancelled());
    }
    @Test @SuppressWarnings("unchecked") void transientRateLimitHasOnlyOneBoundedRetry()throws Exception {
        HttpClient client=mock(HttpClient.class);
        HttpResponse<byte[]> busy=response("busy",429);
        when(busy.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("0")),(a,b)->true));
        doReturn(CompletableFuture.completedFuture(busy)).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var failure=assertThrows(ModelFailure.class,()->gateway(client).complete(request()));
        assertTrue(failure.getMessage().contains("rate limit"));
        verify(client,times(2)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test @SuppressWarnings("unchecked") void nonRetryableProviderErrorsDoNotRetry()throws Exception {
        HttpClient client=mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response("sensitive-debug",400))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThrows(ModelFailure.class,()->gateway(client).complete(request()));
        verify(client,times(1)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }

    @Test @SuppressWarnings("unchecked") void providerRetryAfterBeyondBudgetIsRespectedWithoutEarlyRetry()throws Exception {
        HttpClient client=mock(HttpClient.class);
        HttpResponse<byte[]> busy=response("busy",429);
        when(busy.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("60")),(a,b)->true));
        doReturn(CompletableFuture.completedFuture(busy)).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThrows(ModelFailure.class,()->gateway(client).complete(request()));
        verify(client,times(1)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        String future=java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(1).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(-1,BharatCodeGateway.retryDelay(future));
    }

    @Test @SuppressWarnings("unchecked") void authenticationAndServerFailuresAreTypedAndBounded()throws Exception {
        for(int status:new int[]{401,403,429,500,502,503}) {
            HttpClient client=mock(HttpClient.class);var rejected=response("private-provider-debug",status);
            when(rejected.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("0")),(a,b)->true));
            doReturn(CompletableFuture.completedFuture(rejected)).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
            var error=assertThrows(ModelFailure.class,()->gateway(client).complete(request()));assertFalse(error.getMessage().contains("private-provider"));
            assertEquals(status==401||status==403?"AUTHENTICATION":status==429?"RATE_LIMIT":"UNAVAILABLE",error.classification());
            verify(client,times(status==401||status==403?1:2)).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        }
    }
    @Test @SuppressWarnings("unchecked") void emptyAndMissingChoicesAreNeverValidResponses()throws Exception {
        for(String body:List.of("{\"choices\":[]}","{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}]}")) {
            HttpClient client=mock(HttpClient.class);doReturn(CompletableFuture.completedFuture(response(body,200))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
            assertThrows(ModelFailure.class,()->gateway(client).complete(request()));
        }
    }
    @Test void interruptionNeverBecomesSuccessfulInference() {
        Thread.currentThread().interrupt();
        try{var failure=assertThrows(ModelFailure.class,()->gateway(mock(HttpClient.class)).complete(request()));assertEquals("INTERRUPTED",failure.classification());assertTrue(Thread.currentThread().isInterrupted());}
        finally{Thread.interrupted();}
    }

    @Test @SuppressWarnings("unchecked") void invalidUsageIsUnknownRatherThanInventedOrOverflowing()throws Exception {
        HttpClient client=mock(HttpClient.class);
        doReturn(CompletableFuture.completedFuture(response("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"valid content\"}}],\"usage\":{\"prompt_tokens\":9223372036854775807,\"completion_tokens\":-1}}",200))).when(client).sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var result=gateway(client).complete(request());assertEquals("valid content",result.content());assertFalse(result.metadata().usageAvailable());assertEquals(0,result.inputTokens());assertEquals(1,result.metadata().transportAttempts());
    }

}
