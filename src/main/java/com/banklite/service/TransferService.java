package com.banklite.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banklite.dto.TransferRequest;
import com.banklite.dto.TransferResponse;
import com.banklite.entity.Account;
import com.banklite.entity.LedgerEntry;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.exception.IdempotencyKeyConflictException;
import com.banklite.exception.InsufficientFundsException;
import com.banklite.exception.InvalidRequestException;
import com.banklite.repository.AccountRepository;
import com.banklite.repository.LedgerEntryRepository;

/**
 * One transfer = one database transaction. Concurrency control is optimistic (@Version on Account):
 * a stale write makes the commit fail with ObjectOptimisticLockingFailureException, which is
 * retried by TransferFacade OUTSIDE this transaction.
 */
@Service
public class TransferService {

    static final int MAX_KEY_LENGTH = 128;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerRepository;

    public TransferService(AccountRepository accountRepository, LedgerEntryRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        BigDecimal amount = validate(request);

        if (key != null) {
            Optional<LedgerEntry> existing = ledgerRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return replay(existing.get(), request, amount, key);
            }
        }

        Account from = load(request.fromAccountId());
        Account to = load(request.toAccountId());

        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(from.getId());
        }

        from.debit(amount);
        to.credit(amount);

        // saveAndFlush forces the versioned UPDATEs and the INSERT to run inside the method.
        LedgerEntry saved = ledgerRepository.saveAndFlush(
                new LedgerEntry(request.fromAccountId(), request.toAccountId(), amount, key));
        return TransferResponse.from(saved);
    }

    private TransferResponse replay(LedgerEntry existing, TransferRequest request, BigDecimal amount, String key) {
        boolean samePayload = existing.getFromAccountId().equals(request.fromAccountId())
                && existing.getToAccountId().equals(request.toAccountId())
                && existing.getAmount().compareTo(amount) == 0;
        if (!samePayload) {
            throw new IdempotencyKeyConflictException(key);
        }
        return TransferResponse.from(existing);
    }

    private Account load(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new InvalidRequestException("Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Returns the amount normalised to scale 2. */
    private BigDecimal validate(TransferRequest request) {
        if (request == null || request.fromAccountId() == null || request.toAccountId() == null
                || request.amount() == null) {
            throw new InvalidRequestException("fromAccountId, toAccountId and amount are required");
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new InvalidRequestException("Source and destination accounts must differ");
        }
        BigDecimal amount;
        try {
            amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidRequestException("amount must have at most 2 decimal places");
        }
        if (amount.signum() <= 0) {
            throw new InvalidRequestException("amount must be positive");
        }
        if (amount.precision() > 19) {
            throw new InvalidRequestException("amount is too large");
        }
        return amount;
    }
}
