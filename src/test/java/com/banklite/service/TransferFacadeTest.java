package com.banklite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.banklite.config.CacheConfig;
import com.banklite.dto.TransferRequest;
import com.banklite.dto.TransferResponse;
import com.banklite.entity.Account;
import com.banklite.entity.TransferStatus;
import com.banklite.exception.ConcurrentUpdateException;
import com.banklite.exception.InsufficientFundsException;

@ExtendWith(MockitoExtension.class)
class TransferFacadeTest {

    @Mock
    private TransferService transferService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private TransferFacade facade;

    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();
    private final TransferRequest request = new TransferRequest(fromId, toId, new BigDecimal("5.00"));
    private final TransferResponse response = new TransferResponse(UUID.randomUUID(), fromId, toId,
            new BigDecimal("5.00"), TransferStatus.COMPLETED, null, Instant.now());

    @BeforeEach
    void setUp() {
        lenient().when(cacheManager.getCache(CacheConfig.ACCOUNTS_CACHE)).thenReturn(cache);
        facade = new TransferFacade(transferService, cacheManager, 0L);
    }

    private ObjectOptimisticLockingFailureException lockFailure() {
        return new ObjectOptimisticLockingFailureException(Account.class, "id");
    }

    @Test
    void transfer_success_evictsBothAccountsFromCache() {
        when(transferService.transfer(any(), any())).thenReturn(response);

        TransferResponse result = facade.transfer(request, null);

        assertThat(result).isSameAs(response);
        verify(cache).evict(fromId);
        verify(cache).evict(toId);
        verify(transferService, times(1)).transfer(any(), any());
    }

    @Test
    void transfer_retriesOnOptimisticLockFailureThenSucceeds() {
        when(transferService.transfer(any(), any()))
                .thenThrow(lockFailure())
                .thenThrow(lockFailure())
                .thenReturn(response);

        TransferResponse result = facade.transfer(request, null);

        assertThat(result).isSameAs(response);
        verify(transferService, times(3)).transfer(any(), any());
    }

    @Test
    void transfer_givesUpAfterFiveAttempts() {
        when(transferService.transfer(any(), any())).thenThrow(lockFailure());

        assertThatThrownBy(() -> facade.transfer(request, null))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(transferService, times(TransferFacade.MAX_ATTEMPTS)).transfer(any(), any());
        verify(cache, never()).evict(any());
    }

    @Test
    void transfer_doesNotRetryBusinessErrors() {
        when(transferService.transfer(any(), any())).thenThrow(new InsufficientFundsException(fromId));

        assertThatThrownBy(() -> facade.transfer(request, null))
                .isInstanceOf(InsufficientFundsException.class);

        verify(transferService, times(1)).transfer(any(), any());
        verify(cache, never()).evict(any());
    }

    @Test
    void transfer_duplicateKeyRace_isRetriedAndReplayed() {
        when(transferService.transfer(any(), any()))
                .thenThrow(new DataIntegrityViolationException("unique"))
                .thenReturn(response);

        TransferResponse result = facade.transfer(request, "key-1");

        assertThat(result).isSameAs(response);
        verify(transferService, times(2)).transfer(any(), any());
    }

    @Test
    void transfer_integrityViolationWithoutKey_isRethrown() {
        when(transferService.transfer(any(), any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> facade.transfer(request, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(transferService, times(1)).transfer(any(), any());
    }

    @Test
    void transfer_cacheEvictionFailureDoesNotFailTheTransfer() {
        when(transferService.transfer(any(), any())).thenReturn(response);
        doThrow(new IllegalStateException("redis down")).when(cache).evict(any());

        TransferResponse result = facade.transfer(request, null);

        assertThat(result).isSameAs(response);
    }

    @Test
    void transfer_missingCache_isTolerated() {
        when(cacheManager.getCache(CacheConfig.ACCOUNTS_CACHE)).thenReturn(null);
        when(transferService.transfer(any(), any())).thenReturn(response);

        assertThat(facade.transfer(request, null)).isSameAs(response);
    }
}
