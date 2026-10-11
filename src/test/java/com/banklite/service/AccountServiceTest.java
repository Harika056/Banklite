package com.banklite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.banklite.dto.AccountResponse;
import com.banklite.dto.CreateAccountRequest;
import com.banklite.entity.Account;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.exception.InvalidRequestException;
import com.banklite.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService service;

    @Test
    void createAccount_savesTrimmedOwnerAndScaledDeposit() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.createAccount(new CreateAccountRequest("  Ada  ", new BigDecimal("100")));

        assertThat(response.ownerName()).isEqualTo("Ada");
        assertThat(response.balance()).isEqualByComparingTo("100.00");
        assertThat(response.balance().scale()).isEqualTo(2);
    }

    @Test
    void createAccount_negativeDeposit_isRejected() {
        assertThatThrownBy(() -> service.createAccount(new CreateAccountRequest("Ada", new BigDecimal("-1.00"))))
                .isInstanceOf(InvalidRequestException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createAccount_tooManyDecimals_isRejected() {
        assertThatThrownBy(() -> service.createAccount(new CreateAccountRequest("Ada", new BigDecimal("1.234"))))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createAccount_blankOwner_isRejected() {
        assertThatThrownBy(() -> service.createAccount(new CreateAccountRequest(" ", BigDecimal.ONE)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getAccount_returnsMappedAccount() {
        UUID id = UUID.randomUUID();
        Account account = new Account("Ada", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(account, "id", id);
        when(accountRepository.findById(id)).thenReturn(Optional.of(account));

        AccountResponse response = service.getAccount(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.balance()).isEqualByComparingTo("12.50");
    }

    @Test
    void getAccount_unknown_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount(id)).isInstanceOf(AccountNotFoundException.class);
    }
}
