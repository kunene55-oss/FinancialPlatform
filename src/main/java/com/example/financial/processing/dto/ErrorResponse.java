package com.example.financial.processing.dto;

import java.time.LocalDateTime;

public record ErrorResponse (int status, String message, LocalDateTime timestamp){}
