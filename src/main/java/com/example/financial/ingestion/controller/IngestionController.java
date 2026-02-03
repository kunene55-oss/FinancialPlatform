package com.example.financial.ingestion.controller;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import com.example.financial.ingestion.service.IngestionService;
import com.example.financial.ingestion.dto.TransactionReceivedEvent;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestionController {
    
    private final IngestionService ingestionService;

    @PostMapping
    public Flux<TransactionReceivedEvent> ingest() {
        return ingestionService.ingestMockTransactions();
    }
    
}
