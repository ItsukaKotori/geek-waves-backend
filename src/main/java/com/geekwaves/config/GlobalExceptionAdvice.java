package com.geekwaves.config;

import lombok.extern.slf4j.Slf4j;
import org.itsuka.core.domain.Response;
import org.itsuka.core.exception.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvice {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Response<?>> handleServiceException(ServiceException e) {
        HttpStatus status = e.getStatus() != null ? e.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Response.error(status, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = first != null ? first.getField() + ": " + first.getDefaultMessage() : "参数校验失败";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Response.error(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<?>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Response.error(HttpStatus.NOT_FOUND, "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Response.error(HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<?>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Response.error(HttpStatus.BAD_REQUEST, "请求体格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<?>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.error(HttpStatus.INTERNAL_SERVER_ERROR, "服务异常"));
    }
}
