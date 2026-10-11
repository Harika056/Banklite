package com.banklite.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banklite.config.CacheConfig;
import com.banklite.dto.AccountResponse;
import com.banklite.dto.CreateAccountRequest;
import com.banklite.entity.Account;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.exception.InvalidRequestException;
import com.banklite.repository.AccountRepository;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        if (request == null || request.ownerName() == null || request.ownerName().isBlank()
                || request.initialDeposit() == null) {
            throw new InvalidRequestException("ownerName and initialDeposit are required");
        }
        BigDecimal deposit;
        try {
            deposit = request.initialDeposit().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidRequestException("initialDeposit must have at most 2 decimal places");
        }
        if (deposit.signum() < 0) {
            throw new InvalidRequestException("initialDeposit must not be negative");
        }
        Account saved = accountRepository.save(new Account(request.ownerName().trim(), deposit));
        return AccountResponse.from(saved);
    }

    /** Cached in Redis (TTL configured in CacheConfig); evicted by TransferFacade after a transfer commits. */
    @Cacheable(cacheNames = CacheConfig.ACCOUNTS_CACHE, key = "#root.args[0]")
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
