package com.szu.rag.framework.exception;

import com.szu.rag.framework.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClientException.class)
    public Result<Void> handleClientException(ClientException e, HttpServletRequest request) {
        log.warn("Client error: code={}, msg={}, path={}", e.getErrorCode(), e.getMessage(), request.getRequestURI());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(ServiceException.class)
    public Result<Void> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.error("Service error: code={}, msg={}, path={}", e.getErrorCode(), e.getMessage(), request.getRequestURI());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(RemoteException.class)
    public Result<Void> handleRemoteException(RemoteException e, HttpServletRequest request) {
        log.error("Remote error: code={}, msg={}, path={}", e.getErrorCode(), e.getMessage(), request.getRequestURI());
        return Result.error(e.getErrorCode(), "服务暂时不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error: path={}", request.getRequestURI(), e);
        return Result.error("500", "服务器内部错误");
    }
}
