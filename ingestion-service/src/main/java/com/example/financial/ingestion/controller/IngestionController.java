package com.example.financial.ingestion.controller;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.example.financial.ingestion.database.entities.FileStatus;
import com.example.financial.ingestion.database.entities.FailedPaymentReport;
import com.example.financial.ingestion.database.repository.FailedPaymentReportRepository;
import com.example.financial.ingestion.database.repository.FileRepository;
import com.example.financial.ingestion.dto.FileStatusResponse;
import com.example.financial.ingestion.dto.FileUploadResponse;
import com.example.financial.ingestion.service.IngestionService;
import com.example.financial.common.event.TransactionReceivedEvent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;
    private final FileRepository fileRepo;
    private final FailedPaymentReportRepository failedPaymentReportRepo;

    @PreAuthorize("hasRole('transaction-ingest')")
    @PostMapping("/ingestEvent")
    public ResponseEntity<Void> ingestEvent(@Valid @RequestBody final TransactionReceivedEvent event) {
        ingestionService.publish(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PreAuthorize("hasRole('transaction-ingest')")
    @PostMapping("/fileUpload")
    public ResponseEntity<FileUploadResponse> ingestFile(@RequestParam final MultipartFile file){
        if (file == null || file.isEmpty()) {
            log.info("file cannot be null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        log.info("File ingestion started for file: {}", file.getOriginalFilename());
        try {
            String fileHash = ingestionService.initiateIngestion(file);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new FileUploadResponse(fileHash, FileStatus.PROCESSING));
        } catch (TaskRejectedException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PreAuthorize("hasRole('transaction-ingest')")
    @GetMapping("/files/{fileHash}")
    public ResponseEntity<FileStatusResponse> getFileStatus(@PathVariable final String fileHash) {
        return fileRepo.findByFileHash(fileHash)
            .map(f -> ResponseEntity.ok(new FileStatusResponse(
                f.getFileHash(), f.getStatus(), f.getTransactionCount(), f.getFailedTransactionCount())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('transaction-ingest')")
    @GetMapping("/files/{fileHash}/failed-payments")
    public ResponseEntity<List<FailedPaymentReport>> getFailedPayments(@PathVariable final String fileHash) {
        if (fileRepo.findByFileHash(fileHash).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(failedPaymentReportRepo.findByFileHashOrderByRowNumber(fileHash));
    }

}
