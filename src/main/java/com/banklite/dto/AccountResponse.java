package com.banklite.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.banklite.entity.Account;

/** Serializable because it is stored in Redis with JDK serialization. */
public record AccountResponse(UUID id, String ownerName, BigDecimal balance, Instant createdAt)
        implements Serializable {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerName(), account.getBalance(),
                account.getCreatedAt());
    }
}
