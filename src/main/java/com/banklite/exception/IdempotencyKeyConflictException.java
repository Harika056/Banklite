package com.banklite.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}
