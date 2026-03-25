package com.example.financial.processing.dto;

public record CreationRequest(
    String firstName,
    String lastName,
    Long idNumber
) {
    
}
