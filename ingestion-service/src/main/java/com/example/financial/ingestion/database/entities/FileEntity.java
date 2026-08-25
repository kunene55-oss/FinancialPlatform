package com.example.financial.ingestion.database.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEntity {

        @Id
        private String fileHash;

        private int transactionCount;

        private int failedTransactionCount;

        @Enumerated(EnumType.STRING)
        private FileStatus status;
}