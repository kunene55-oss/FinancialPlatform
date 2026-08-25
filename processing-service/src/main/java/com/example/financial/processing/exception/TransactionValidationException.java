package com.example.financial.processing.exception;

public class TransactionValidationException extends RuntimeException {
    public TransactionValidationException(String message) {
        super(message);
    }
}
