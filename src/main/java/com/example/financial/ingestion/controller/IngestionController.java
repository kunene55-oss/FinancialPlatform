package com.example.financial.ingestion.controller;

import org.springframework.web.bind.annotation.*;
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

    @PostMapping("/ingestEvent")
    public void ingestEvent(@RequestBody final TransactionReceivedEvent event) {
        ingestionService.publish(event);
    }
    
    @PostMapping("/fileUpload")
    public void ingestFile(@RequestParam final MultipartFile file){
        if (file == null) {
            log.info("file name cannot be null");
            //Bad request
            return;
        }
        log.info("File ingestion started for file: {}", file.getOriginalFilename());
        ingestionService.ingestFile(file);
    }

}
