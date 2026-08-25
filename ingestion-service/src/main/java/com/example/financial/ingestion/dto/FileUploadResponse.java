package com.example.financial.ingestion.dto;

import com.example.financial.ingestion.database.entities.FileStatus;

public record FileUploadResponse(String fileHash, FileStatus status) {
}
