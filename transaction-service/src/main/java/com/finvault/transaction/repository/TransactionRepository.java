package com.finvault.transaction.repository;

import com.finvault.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReferenceId(String referenceId);

    boolean existsByReferenceId(String referenceId);

    // All transactions involving a user's account (sent or received)
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.fromAccount = :accountNumber
           OR t.toAccount   = :accountNumber
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByAccount(String accountNumber, Pageable pageable);

    // All transactions initiated by a user
    Page<Transaction> findByInitiatedByOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
