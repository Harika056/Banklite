package com.banklite.exception;

public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
