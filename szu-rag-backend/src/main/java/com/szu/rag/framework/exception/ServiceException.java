package com.szu.rag.framework.exception;

public class ServiceException extends BaseException {
    public ServiceException(String errorCode, String message) {
        super("B" + errorCode, message);
    }
}
