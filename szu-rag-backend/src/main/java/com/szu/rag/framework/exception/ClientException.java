package com.szu.rag.framework.exception;

public class ClientException extends BaseException {
    public ClientException(String errorCode, String message) {
        super(errorCode, message);
    }
}
