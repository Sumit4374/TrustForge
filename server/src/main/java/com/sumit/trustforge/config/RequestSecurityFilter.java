package com.sumit.trustforge.config;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestSecurityFilter extends OncePerRequestFilter {
    private static final int MAX_BODY_BYTES=262144;
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String id=UUID.randomUUID().toString();MDC.put("correlationId",id);
        res.setHeader("X-Request-ID",id);res.setHeader("Cache-Control","no-store");
        res.setHeader("X-Robots-Tag","noindex, nofollow");
        res.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=()");
        try {
            if(req.getContentLengthLong()>MAX_BODY_BYTES){tooLarge(res);return;}
            if(Set.of("POST","PUT","PATCH","DELETE").contains(req.getMethod())) {
                byte[] bytes=req.getInputStream().readNBytes(MAX_BODY_BYTES+1);
                if(bytes.length>MAX_BODY_BYTES){tooLarge(res);return;}
                chain.doFilter(new BoundedRequest(req,bytes),res);
            } else chain.doFilter(req,res);
        } finally {MDC.remove("correlationId");}
    }
    private static void tooLarge(HttpServletResponse res)throws IOException {
        res.setStatus(413);res.setContentType("application/problem+json");
        res.getWriter().write("{\"status\":413,\"title\":\"Payload too large\",\"detail\":\"Request body exceeds 256 KiB\"}");
    }
    private static final class BoundedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BoundedRequest(HttpServletRequest request,byte[] body){super(request);this.body=body;}
        @Override public int getContentLength(){return body.length;}
        @Override public long getContentLengthLong(){return body.length;}
        @Override public ServletInputStream getInputStream() {
            var source=new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read(){return source.read();}
                @Override public int read(byte[] b,int off,int len){return source.read(b,off,len);}
                @Override public boolean isFinished(){return source.available()==0;}
                @Override public boolean isReady(){return true;}
                @Override public void setReadListener(ReadListener listener){throw new IllegalStateException("Asynchronous request body reads are not supported");}
            };
        }
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
