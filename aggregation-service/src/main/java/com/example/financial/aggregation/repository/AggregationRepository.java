package com.example.financial.aggregation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.financial.aggregation.domain.TransactionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AggregationRepository extends JpaRepository<TransactionEntity, UUID> {

    @Query("""
        SELECT t.category, SUM(t.amount)
        FROM TransactionEntity t
        WHERE t.accountId = :accountId AND t.status = 'PROCESSED'
        AND (:from IS NULL OR t.timestamp >= :from)
        AND (:to IS NULL OR t.timestamp <= :to)
        GROUP BY t.category
        """)
    List<Object[]> summarizeAccountHistory(@Param("accountId") String accountId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    @Query(value = """
        SELECT date_trunc(:interval, t.timestamp) AS bucket, SUM(t.amount), COUNT(*)
        FROM transactions t
        WHERE t.account_id = :accountId AND t.status = 'PROCESSED'
        AND (CAST(:from AS timestamp) IS NULL OR t.timestamp >= :from)
        AND (CAST(:to AS timestamp) IS NULL OR t.timestamp <= :to)
        GROUP BY bucket
        ORDER BY bucket
        """, nativeQuery = true)
    List<Object[]> trendByInterval(@Param("accountId") String accountId,
                                    @Param("interval") String interval,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query(value = """
        SELECT t.merchant, SUM(t.amount), COUNT(t)
        FROM TransactionEntity t
        WHERE t.accountId = :accountId AND t.status = 'PROCESSED'
        AND (:from IS NULL OR t.timestamp >= :from)
        AND (:to IS NULL OR t.timestamp <= :to)
        GROUP BY t.merchant
        """,
        countQuery = """
        SELECT COUNT(DISTINCT t.merchant)
        FROM TransactionEntity t
        WHERE t.accountId = :accountId AND t.status = 'PROCESSED'
        AND (:from IS NULL OR t.timestamp >= :from)
        AND (:to IS NULL OR t.timestamp <= :to)
        """)
    Page<Object[]> merchantBreakdown(@Param("accountId") String accountId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      Pageable pageable);

    @Query("""
        SELECT t.status, SUM(t.amount), COUNT(t)
        FROM TransactionEntity t
        WHERE t.accountId = :accountId
        AND (:from IS NULL OR t.timestamp >= :from)
        AND (:to IS NULL OR t.timestamp <= :to)
        GROUP BY t.status
        """)
    List<Object[]> statusBreakdown(@Param("accountId") String accountId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query("""
        SELECT t FROM TransactionEntity t
        WHERE t.accountId = :accountId
        AND (:from IS NULL OR t.timestamp >= :from)
        AND (:to IS NULL OR t.timestamp <= :to)
        ORDER BY t.timestamp DESC
        """)
    Page<TransactionEntity> findByAccount(@Param("accountId") String accountId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to,
                                           Pageable pageable);

    @Query("""
        SELECT t.accountId, SUM(t.amount)
        FROM TransactionEntity t
        WHERE t.accountId IN :accountIds AND t.status = 'PROCESSED'
        AND (:from IS NULL OR t.timestamp >= :from)
        AND (:to IS NULL OR t.timestamp <= :to)
        GROUP BY t.accountId
        """)
    List<Object[]> summarizeAccounts(@Param("accountIds") List<String> accountIds,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

}
