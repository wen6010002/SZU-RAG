package com.szu.rag.framework.exception;

public class RemoteException extends BaseException {
    public RemoteException(String errorCode, String message) {
        super("C" + errorCode, message);
    }
}
