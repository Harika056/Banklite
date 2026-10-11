package com.banklite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banklite.dto.LedgerEntryResponse;
import com.banklite.entity.LedgerEntry;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.repository.AccountRepository;
import com.banklite.repository.LedgerEntryRepository;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerRepository;

    @InjectMocks
    private LedgerService service;

    @Test
    void getLedger_unknownAccount_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.getLedger(id)).isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(ledgerRepository);
    }

    @Test
    void getLedger_mapsEntries() {
        UUID id = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(accountRepository.existsById(id)).thenReturn(true);
        when(ledgerRepository.findAllForAccount(id))
                .thenReturn(List.of(new LedgerEntry(id, other, new BigDecimal("3.00"), null)));

        List<LedgerEntryResponse> result = service.getLedger(id);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fromAccountId()).isEqualTo(id);
        assertThat(result.get(0).amount()).isEqualByComparingTo("3.00");
    }
}
