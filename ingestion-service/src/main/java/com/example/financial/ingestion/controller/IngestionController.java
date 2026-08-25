package com.example.financial.ingestion.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.example.financial.ingestion.service.IngestionService;
import com.example.financial.common.event.TransactionReceivedEvent;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PreAuthorize("hasRole('transaction-ingest')")
    @PostMapping("/ingestEvent")
    public ResponseEntity<Void> ingestEvent(@Valid @RequestBody final TransactionReceivedEvent event) {
        ingestionService.publish(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PreAuthorize("hasRole('transaction-ingest')")
    @PostMapping("/fileUpload")
    public ResponseEntity<Void> ingestFile(@RequestParam final MultipartFile file){
        if (file == null || file.isEmpty()) {
            log.info("file cannot be null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        log.info("File ingestion started for file: {}", file.getOriginalFilename());
        ingestionService.ingestFile(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

}
