package com.example.financial.processing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreationRequest(
    @NotBlank
    @Size(max = 20)
    String firstName,

    @NotBlank
    @Size(max = 20)
    String lastName,

    @NotNull
    Long idNumber
) {
    
}
