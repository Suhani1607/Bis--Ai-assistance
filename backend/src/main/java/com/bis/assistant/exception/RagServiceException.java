package com.bis.assistant.exception;

public class RagServiceException extends RuntimeException {
    public RagServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
