package com.banklite.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.banklite.entity.LedgerEntry;
import com.banklite.entity.TransferStatus;

public record TransferResponse(UUID id, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                               TransferStatus status, String idempotencyKey, Instant createdAt) {

    public static TransferResponse from(LedgerEntry entry) {
        return new TransferResponse(entry.getId(), entry.getFromAccountId(), entry.getToAccountId(),
                entry.getAmount(), entry.getStatus(), entry.getIdempotencyKey(), entry.getCreatedAt());
    }
}
