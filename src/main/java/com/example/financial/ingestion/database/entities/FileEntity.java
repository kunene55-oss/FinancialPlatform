package com.example.financial.ingestion.database.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.Builder;

@Entity
@Table(name = "files")
@Data
@Builder
public class FileEntity {
    
        @Id
        private String fileHash;

        private int transactionCount;
}