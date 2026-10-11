package com.banklite.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.banklite.entity.LedgerEntry;
import com.banklite.entity.TransferStatus;

public record LedgerEntryResponse(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                                  TransferStatus status, Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getFromAccountId(), entry.getToAccountId(),
                entry.getAmount(), entry.getStatus(), entry.getCreatedAt());
    }
}
