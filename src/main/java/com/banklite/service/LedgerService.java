package com.banklite.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banklite.dto.LedgerEntryResponse;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.repository.AccountRepository;
import com.banklite.repository.LedgerEntryRepository;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerRepository;

    public LedgerService(AccountRepository accountRepository, LedgerEntryRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedger(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return ledgerRepository.findAllForAccount(accountId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }
}
