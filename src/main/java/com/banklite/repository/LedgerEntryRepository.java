package com.banklite.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.banklite.entity.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    @Query("select l from LedgerEntry l where l.fromAccountId = :accountId or l.toAccountId = :accountId "
            + "order by l.createdAt desc")
    List<LedgerEntry> findAllForAccount(@Param("accountId") UUID accountId);
}
