package com.banklite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.banklite.dto.TransferRequest;
import com.banklite.dto.TransferResponse;
import com.banklite.entity.Account;
import com.banklite.entity.LedgerEntry;
import com.banklite.entity.TransferStatus;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.exception.IdempotencyKeyConflictException;
import com.banklite.exception.InsufficientFundsException;
import com.banklite.exception.InvalidRequestException;
import com.banklite.repository.AccountRepository;
import com.banklite.repository.LedgerEntryRepository;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerRepository;

    @InjectMocks
    private TransferService service;

    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();

    private Account account(UUID id, String balance) {
        Account account = new Account("owner", new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private TransferRequest request(String amount) {
        return new TransferRequest(fromId, toId, new BigDecimal(amount));
    }

    private void stubAccounts(Account from, Account to) {
        when(accountRepository.findById(fromId)).thenReturn(Optional.of(from));
        when(accountRepository.findById(toId)).thenReturn(Optional.of(to));
    }

    private void stubSave() {
        when(ledgerRepository.saveAndFlush(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void transfer_movesMoneyAndRecordsLedgerEntry() {
        Account from = account(fromId, "100.00");
        Account to = account(toId, "10.00");
        stubAccounts(from, to);
        stubSave();

        TransferResponse response = service.transfer(request("30.00"), null);

        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("40.00");
        assertThat(response.amount()).isEqualByComparingTo("30.00");
        assertThat(response.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.fromAccountId()).isEqualTo(fromId);
        assertThat(response.toAccountId()).isEqualTo(toId);
    }

    @Test
    void transfer_storesIdempotencyKeyOnLedgerEntry() {
        stubAccounts(account(fromId, "100.00"), account(toId, "0.00"));
        stubSave();
        when(ledgerRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        service.transfer(request("5.00"), "  key-1  ");

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void transfer_entireBalanceIsAllowed() {
        Account from = account(fromId, "50.00");
        stubAccounts(from, account(toId, "0.00"));
        stubSave();

        service.transfer(request("50.00"), null);

        assertThat(from.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void transfer_normalizesAmountToTwoDecimals() {
        stubAccounts(account(fromId, "100.00"), account(toId, "0.00"));
        stubSave();

        TransferResponse response = service.transfer(request("5"), null);

        assertThat(response.amount().scale()).isEqualTo(2);
    }

    @Test
    void transfer_insufficientFunds_throwsAndWritesNothing() {
        Account from = account(fromId, "10.00");
        Account to = account(toId, "0.00");
        stubAccounts(from, to);

        assertThatThrownBy(() -> service.transfer(request("10.01"), null))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(from.getBalance()).isEqualByComparingTo("10.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
        verify(ledgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_sameAccount_isRejected() {
        TransferRequest same = new TransferRequest(fromId, fromId, new BigDecimal("1.00"));

        assertThatThrownBy(() -> service.transfer(same, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("differ");
        verifyNoInteractions(accountRepository);
    }

    @Test
    void transfer_zeroAmount_isRejected() {
        assertThatThrownBy(() -> service.transfer(request("0.00"), null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void transfer_negativeAmount_isRejected() {
        assertThatThrownBy(() -> service.transfer(request("-5.00"), null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void transfer_moreThanTwoDecimals_isRejected() {
        assertThatThrownBy(() -> service.transfer(request("1.001"), null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("2 decimal");
    }

    @Test
    void transfer_nullAmount_isRejected() {
        TransferRequest bad = new TransferRequest(fromId, toId, null);

        assertThatThrownBy(() -> service.transfer(bad, null)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void transfer_tooLongIdempotencyKey_isRejected() {
        String longKey = "k".repeat(TransferService.MAX_KEY_LENGTH + 1);

        assertThatThrownBy(() -> service.transfer(request("1.00"), longKey))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void transfer_unknownSource_throwsNotFound() {
        when(accountRepository.findById(fromId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(request("1.00"), null))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(fromId.toString());
    }

    @Test
    void transfer_unknownDestination_throwsNotFound() {
        when(accountRepository.findById(fromId)).thenReturn(Optional.of(account(fromId, "10.00")));
        when(accountRepository.findById(toId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(request("1.00"), null))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(toId.toString());
    }

    @Test
    void transfer_idempotentReplay_returnsOriginalWithoutReapplying() {
        LedgerEntry original = new LedgerEntry(fromId, toId, new BigDecimal("25.00"), "key-1");
        UUID originalId = UUID.randomUUID();
        ReflectionTestUtils.setField(original, "id", originalId);
        when(ledgerRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        TransferResponse response = service.transfer(request("25.00"), "key-1");

        assertThat(response.id()).isEqualTo(originalId);
        verifyNoInteractions(accountRepository);
        verify(ledgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_idempotencyKeyReusedWithDifferentPayload_conflicts() {
        LedgerEntry original = new LedgerEntry(fromId, toId, new BigDecimal("25.00"), "key-1");
        when(ledgerRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.transfer(request("26.00"), "key-1"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        verify(ledgerRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_blankIdempotencyKey_isTreatedAsAbsent() {
        stubAccounts(account(fromId, "10.00"), account(toId, "0.00"));
        stubSave();

        service.transfer(request("1.00"), "   ");

        verify(ledgerRepository, never()).findByIdempotencyKey(any());
    }
}
