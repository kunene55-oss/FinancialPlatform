package com.example.financial.aggregation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.example.financial.aggregation.domain.TransactionEntity;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface AggregationRepository extends JpaRepository<TransactionEntity, UUID> {
    
    @Query("""
        SELECT t.category, SUM(t.amount)
        FROM TransactionEntity t
        WHERE t.accountId = :accountId
        GROUP BY t.category
        """)
    List<Object[]> summarizeAccountHistory(String accountId);
    
}
