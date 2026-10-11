package com.banklite.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.banklite.entity.Account;
import com.banklite.repository.AccountRepository;
import com.banklite.repository.LedgerEntryRepository;

@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockingIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @BeforeEach
    void clean() {
        ledgerRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
    }

    @Test
    void newAccountStartsAtVersionZeroAndGetsIdAssigned() {
        Account saved = accountRepository.save(new Account("A", new BigDecimal("1.00")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void staleWriteIsRejected() {
        UuidHolder holder = new UuidHolder(accountRepository.save(new Account("A", new BigDecimal("100.00"))));

        // Two detached copies of the same row at the same version.
        Account copy1 = accountRepository.findById(holder.account.getId()).orElseThrow();
        Account copy2 = accountRepository.findById(holder.account.getId()).orElseThrow();

        copy1.credit(new BigDecimal("10.00"));
        accountRepository.saveAndFlush(copy1);

        copy2.credit(new BigDecimal("20.00"));
        assertThatThrownBy(() -> accountRepository.saveAndFlush(copy2))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        Account stored = accountRepository.findById(holder.account.getId()).orElseThrow();
        assertThat(stored.getBalance()).isEqualByComparingTo("110.00");
        assertThat(stored.getVersion()).isEqualTo(1L);
    }

    private static final class UuidHolder {
        private final Account account;

        private UuidHolder(Account account) {
            this.account = account;
        }
    }
}
