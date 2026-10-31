package com.sumit.trustforge.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Explicit bounded type tags; no default typing or arbitrary class deserialization. */
public final class SafeSessionSerializer implements RedisSerializer<Object> {
    private final ObjectMapper mapper=new ObjectMapper();
    @Override public byte[] serialize(Object value) {
        if(value==null)return new byte[0];
        try {
            byte[] bytes=mapper.writeValueAsBytes(encode(value,0));
            if(bytes.length>65536)throw new SerializationException("Session attribute exceeds size limit");
            return bytes;
        }catch(SerializationException e){throw e;}
        catch(Exception e){throw new SerializationException("Session encoding failed",e);}
    }
    private Map<String,Object> encode(Object value,int depth) {
        if(depth>4)throw new SerializationException("Session nesting exceeds limit");
        Map<String,Object> data=new LinkedHashMap<>();
        if(value==null){data.put("type","null");}
        else if(value instanceof SecurityContext context) {
            var a=context.getAuthentication();data.put("type","security");
            if(a!=null){data.put("name",a.getName());data.put("roles",a.getAuthorities().stream().map(x->x.getAuthority()).toList());}
        } else if(value instanceof CsrfToken token) {
            data.put("type","csrf");data.put("token",token.getToken());data.put("header",token.getHeaderName());data.put("parameter",token.getParameterName());
        } else if(value instanceof String){data.put("type","string");data.put("value",value);}
        else if(value instanceof Integer){data.put("type","integer");data.put("value",value);}
        else if(value instanceof Long){data.put("type","long");data.put("value",value);}
        else if(value instanceof Boolean){data.put("type","boolean");data.put("value",value);}
        else if(value instanceof Map<?,?> values) {
            // Indexed Redis sessions publish a creation-event map of their whitelisted metadata/attributes.
            if(values.size()>64)throw new SerializationException("Too many session metadata fields");
            Map<String,Object> encoded=new LinkedHashMap<>();
            for(var entry:values.entrySet()) {
                if(!(entry.getKey() instanceof String key)||key.length()>160)throw new SerializationException("Invalid session metadata key");
                encoded.put(key,encode(entry.getValue(),depth+1));
            }
            data.put("type","map");data.put("value",encoded);
        } else throw new SerializationException("Unsupported session attribute type");
        return data;
    }
    @Override public Object deserialize(byte[] bytes) {
        if(bytes==null||bytes.length==0)return null;
        if(bytes.length>65536)throw new SerializationException("Session attribute exceeds size limit");
        try{return decode(mapper.readTree(bytes),0);}
        catch(Exception e){throw new SerializationException("Session decoding failed",e);}
    }
    private Object decode(JsonNode n,int depth) {
        if(depth>4||!n.isObject())throw new SerializationException("Invalid session shape");
        return switch(n.path("type").asText()) {
            case "null" -> null;
            case "security" -> {
                if(!n.has("name"))yield new SecurityContextImpl();
                List<SimpleGrantedAuthority> roles=new java.util.ArrayList<>();
                if(!n.path("roles").isArray()||n.path("roles").size()>2)throw new SerializationException("Invalid authority list");
                for(JsonNode role:n.path("roles")) {
                    String r=role.asText();
                    if(!r.equals("ROLE_USER")&&!r.equals("ROLE_ADMIN"))throw new SerializationException("Invalid authority");
                    roles.add(new SimpleGrantedAuthority(r));
                }
                yield new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(n.path("name").asText(),null,roles));
            }
            case "csrf" -> new DefaultCsrfToken(n.path("header").asText(),n.path("parameter").asText(),n.path("token").asText());
            case "string" -> {if(!n.path("value").isTextual())throw new SerializationException("Invalid string value");yield n.path("value").asText();}
            case "integer" -> {if(!n.path("value").isInt())throw new SerializationException("Invalid integer value");yield n.path("value").asInt();}
            case "long" -> {if(!n.path("value").isIntegralNumber())throw new SerializationException("Invalid long value");yield n.path("value").asLong();}
            case "boolean" -> {if(!n.path("value").isBoolean())throw new SerializationException("Invalid boolean value");yield n.path("value").asBoolean();}
            case "map" -> {
                JsonNode values=n.path("value");
                if(!values.isObject()||values.size()>64)throw new SerializationException("Invalid session metadata");
                Map<String,Object> decoded=new LinkedHashMap<>();
                for(String key:values.propertyNames()) {
                    if(key.length()>160)throw new SerializationException("Invalid session metadata key");
                    decoded.put(key,decode(values.path(key),depth+1));
                }
                yield decoded;
            }
            default -> throw new SerializationException("Unknown session attribute type");
        };
    }
}
