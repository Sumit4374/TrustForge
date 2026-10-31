package com.sumit.trustforge.ai;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;

/** Provider boundary: callers must validate all returned model content. */
public interface AIModelGateway {
    record Request(UUID actorId, String system, String input, Instant deadline, String model) {
        @Override public String toString(){return "Model request [content redacted]";}
        public Request(UUID actorId, String system, String input, Instant deadline) {
            this(actorId, system, input, deadline, null);
        }
    }

    record Metadata(String model,Instant requestedAt,long durationMs,int retries,boolean usageAvailable,Integer transportAttempts) {
        public Metadata(String model,Instant requestedAt,long durationMs,int retries,boolean usageAvailable){this(model,requestedAt,durationMs,retries,usageAvailable,null);}
    }
    record Descriptor(String provider,String model,Map<String,Object> inferenceParameters) {
        public Descriptor {inferenceParameters=Map.copyOf(inferenceParameters);}
    }
    record Response(String content,long inputTokens,long outputTokens,String requestId,Metadata metadata) {
        public Response(String content,long inputTokens,long outputTokens,String requestId){this(content,inputTokens,outputTokens,requestId,null);}
        @Override public String toString(){return "Model response [content redacted]";}
    }
    default Descriptor descriptor(String model){return new Descriptor("UNSPECIFIED_PROVIDER",model,Map.of());}

    Response complete(Request request);

    boolean configured();
}
