package com.sumit.trustforge.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ProblemDetail status(org.springframework.web.server.ResponseStatusException e) { return ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()==null?"Request failed":e.getReason()); }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ProblemDetail contentType(){return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Use application/json for request bodies");}
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ProblemDetail method(){return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,"This HTTP method is not supported");}
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ProblemDetail parameter(){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid or missing request parameter");}
    @ExceptionHandler(ApiException.class) ProblemDetail api(ApiException e) { return ProblemDetail.forStatusAndDetail(e.status(),e.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ProblemDetail invalid(Exception e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid request. Check field values and limits."); }
    @ExceptionHandler(org.springframework.security.web.authentication.session.SessionAuthenticationException.class)
    ProblemDetail sessions(){return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,"Session limit reached. Sign out from another browser or wait for expiry.");}
    @ExceptionHandler(AccessDeniedException.class) ProblemDetail denied() { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Access denied"); }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"The resource changed or conflicts with existing data. Refresh and retry."); }
    @ExceptionHandler({org.springframework.data.redis.RedisConnectionFailureException.class,org.springframework.data.redis.RedisSystemException.class,org.springframework.dao.QueryTimeoutException.class,org.springframework.dao.DataAccessResourceFailureException.class,org.springframework.transaction.CannotCreateTransactionException.class})
    ProblemDetail infrastructure(Exception failure){return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Secure session or required infrastructure is temporarily unavailable. Please retry.");}
    @ExceptionHandler(org.springframework.orm.jpa.JpaSystemException.class)
    ProblemDetail persistence(org.springframework.orm.jpa.JpaSystemException failure){return com.sumit.trustforge.config.SessionInfrastructureFilter.redisFailure(failure)?infrastructure(failure):failure(failure);}
    @ExceptionHandler(Exception.class) ProblemDetail failure(Exception e) {
        org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("request_failed type={}",e.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,"The request could not be completed");
    }
}
