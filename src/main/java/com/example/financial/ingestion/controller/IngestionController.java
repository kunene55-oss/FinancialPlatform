package com.example.financial.ingestion.controller;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import com.example.financial.ingestion.service.IngestionService;
import com.example.financial.common.event.TransactionReceivedEvent;

@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestionController {
    
    private final IngestionService ingestionService;

    @PostMapping
    public void ingestEvent(@RequestBody final TransactionReceivedEvent event) {
        ingestionService.publish(event);
    }
    
    // TODO :Add endpoint for reading from file
    @PostMapping("/fileUpload")
    public void ingestFile(){
        return;
    }

    // TODO: add S3 polling endpoint
}
