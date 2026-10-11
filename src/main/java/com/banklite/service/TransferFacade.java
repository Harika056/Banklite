package com.banklite.service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.banklite.config.CacheConfig;
import com.banklite.dto.TransferRequest;
import com.banklite.dto.TransferResponse;
import com.banklite.exception.ConcurrentUpdateException;

/**
 * Deliberately NOT @Transactional. Each attempt calls TransferService, which opens and commits its
 * own transaction; a failed attempt has fully rolled back before the next one starts and re-reads
 * fresh versions. After a committed transfer the affected cache entries are evicted.
 */
@Service
public class TransferFacade {

    static final int MAX_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(TransferFacade.class);

    private final TransferService transferService;
    private final CacheManager cacheManager;
    private final long backoffMillis;

    @Autowired
    public TransferFacade(TransferService transferService, CacheManager cacheManager,
                          @Value("${banklite.transfer.retry-backoff-ms:10}") long backoffMillis) {
        this.transferService = transferService;
        this.cacheManager = cacheManager;
        this.backoffMillis = backoffMillis;
    }

    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                TransferResponse response = transferService.transfer(request, idempotencyKey);
                evict(response.fromAccountId());
                evict(response.toAccountId());
                return response;
            } catch (ObjectOptimisticLockingFailureException e) {
                lastFailure = e;
            } catch (DataIntegrityViolationException e) {
                // Two requests with the same new Idempotency-Key raced on the unique constraint.
                // Retrying lets the loser find the winner's ledger entry and replay it.
                if (idempotencyKey == null || idempotencyKey.isBlank()) {
                    throw e;
                }
                lastFailure = e;
            }
            if (attempt < MAX_ATTEMPTS && !backoff(attempt)) {
                break;
            }
        }
        throw new ConcurrentUpdateException("Transfer failed after " + MAX_ATTEMPTS + " attempts", lastFailure);
    }

    /** Linear backoff with jitter. Returns false if the thread was interrupted. */
    private boolean backoff(int attempt) {
        if (backoffMillis <= 0) {
            return true;
        }
        long sleep = backoffMillis * attempt + ThreadLocalRandom.current().nextLong(backoffMillis);
        try {
            Thread.sleep(sleep);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void evict(UUID accountId) {
        try {
            Cache cache = cacheManager.getCache(CacheConfig.ACCOUNTS_CACHE);
            if (cache != null) {
                cache.evict(accountId);
            }
        } catch (RuntimeException e) {
            log.warn("Could not evict account {} from cache: {}", accountId, e.toString());
        }
    }
}
