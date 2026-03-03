package com.example.financial.ingestion.database.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Table(name = "files")
@Data
public class FileEntity {
    
        @Id
        private String fileHash;

        private int transactionCount;
}