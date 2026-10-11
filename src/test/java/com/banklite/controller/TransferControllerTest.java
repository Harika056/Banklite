package com.banklite.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.banklite.dto.TransferResponse;
import com.banklite.entity.Account;
import com.banklite.entity.TransferStatus;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.exception.ConcurrentUpdateException;
import com.banklite.exception.IdempotencyKeyConflictException;
import com.banklite.exception.InsufficientFundsException;
import com.banklite.exception.InvalidRequestException;
import com.banklite.service.TransferFacade;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferFacade transferFacade;

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    private String body(String amount) {
        return String.format("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":%s}", from, to, amount);
    }

    private ResultActions postTransfer(String json) throws Exception {
        return mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private TransferResponse response(String key) {
        return new TransferResponse(UUID.randomUUID(), from, to, new BigDecimal("10.00"),
                TransferStatus.COMPLETED, key, Instant.now());
    }

    @Test
    void transfer_valid_returns201() throws Exception {
        when(transferFacade.transfer(any(), any())).thenReturn(response(null));

        postTransfer(body("10.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fromAccountId").value(from.toString()))
                .andExpect(jsonPath("$.amount").value(10.0));
    }

    @Test
    void transfer_passesIdempotencyKeyHeaderToFacade() throws Exception {
        when(transferFacade.transfer(any(), eq("abc-123"))).thenReturn(response("abc-123"));

        mockMvc.perform(post("/api/transfers").header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(body("10.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotencyKey").value("abc-123"));

        verify(transferFacade).transfer(any(), eq("abc-123"));
    }

    @Test
    void transfer_zeroAmount_returns400() throws Exception {
        postTransfer(body("0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));
        verifyNoInteractions(transferFacade);
    }

    @Test
    void transfer_negativeAmount_returns400() throws Exception {
        postTransfer(body("-5.00")).andExpect(status().isBadRequest());
    }

    @Test
    void transfer_tooManyDecimals_returns400() throws Exception {
        postTransfer(body("1.234")).andExpect(status().isBadRequest());
    }

    @Test
    void transfer_missingFields_returns400() throws Exception {
        postTransfer("{\"amount\":10.00}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("fromAccountId"))))
                .andExpect(jsonPath("$.details", hasItem(containsString("toAccountId"))));
    }

    @Test
    void transfer_invalidUuidInBody_returns400() throws Exception {
        postTransfer("{\"fromAccountId\":\"x\",\"toAccountId\":\"y\",\"amount\":1.00}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_sameAccount_returns400() throws Exception {
        when(transferFacade.transfer(any(), any()))
                .thenThrow(new InvalidRequestException("Source and destination accounts must differ"));

        postTransfer(body("1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source and destination accounts must differ"));
    }

    @Test
    void transfer_unknownAccount_returns404() throws Exception {
        when(transferFacade.transfer(any(), any())).thenThrow(new AccountNotFoundException(from));

        postTransfer(body("1.00")).andExpect(status().isNotFound());
    }

    @Test
    void transfer_insufficientFunds_returns422() throws Exception {
        when(transferFacade.transfer(any(), any())).thenThrow(new InsufficientFundsException(from));

        postTransfer(body("1.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void transfer_retriesExhausted_returns409() throws Exception {
        when(transferFacade.transfer(any(), any())).thenThrow(new ConcurrentUpdateException("x",
                new RuntimeException(new Account("a", BigDecimal.ONE).toString())));

        postTransfer(body("1.00")).andExpect(status().isConflict());
    }

    @Test
    void transfer_idempotencyKeyConflict_returns409() throws Exception {
        when(transferFacade.transfer(any(), any())).thenThrow(new IdempotencyKeyConflictException("k"));

        postTransfer(body("1.00")).andExpect(status().isConflict());
    }

    @Test
    void transfer_unexpectedError_returns500WithoutLeakingDetails() throws Exception {
        when(transferFacade.transfer(any(), any())).thenThrow(new IllegalStateException("secret-internal-detail"));

        postTransfer(body("1.00"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(jsonPath("$.message", not(containsString("secret"))));
    }

    @Test
    void transfer_wrongMethod_returns405Json() throws Exception {
        mockMvc.perform(get("/api/transfers"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
