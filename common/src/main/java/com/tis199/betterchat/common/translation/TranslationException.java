package com.tis199.betterchat.common.translation;

public final class TranslationException extends RuntimeException {
    private final int statusCode;

    public TranslationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public TranslationException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }
}
