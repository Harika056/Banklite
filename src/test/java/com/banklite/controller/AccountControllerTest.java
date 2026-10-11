package com.banklite.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.banklite.dto.AccountResponse;
import com.banklite.dto.LedgerEntryResponse;
import com.banklite.entity.TransferStatus;
import com.banklite.exception.AccountNotFoundException;
import com.banklite.service.AccountService;
import com.banklite.service.LedgerService;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private LedgerService ledgerService;

    private final UUID id = UUID.randomUUID();

    private AccountResponse sample() {
        return new AccountResponse(id, "Ada", new BigDecimal("100.00"), Instant.now());
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        when(accountService.createAccount(any())).thenReturn(sample());

        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ada\",\"initialDeposit\":100.00}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/accounts/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ownerName").value("Ada"))
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    void create_blankOwner_returns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"  \",\"initialDeposit\":10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasItem(containsString("ownerName"))));
        verifyNoInteractions(accountService);
    }

    @Test
    void create_negativeDeposit_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ada\",\"initialDeposit\":-1.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasItem(containsString("initialDeposit"))));
    }

    @Test
    void create_missingDeposit_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ada\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void get_existing_returns200() throws Exception {
        when(accountService.getAccount(id)).thenReturn(sample());

        mockMvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Ada"));
    }

    @Test
    void get_unknown_returns404Json() throws Exception {
        when(accountService.getAccount(id)).thenThrow(new AccountNotFoundException(id));

        mockMvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString(id.toString())));
    }

    @Test
    void get_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/accounts/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void ledger_returnsEntries() throws Exception {
        UUID other = UUID.randomUUID();
        when(ledgerService.getLedger(id)).thenReturn(List.of(
                new LedgerEntryResponse(UUID.randomUUID(), id, other, new BigDecimal("5.00"),
                        TransferStatus.COMPLETED, Instant.now())));

        mockMvc.perform(get("/api/accounts/{id}/ledger", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].toAccountId").value(other.toString()));
    }

    @Test
    void ledger_unknownAccount_returns404() throws Exception {
        when(ledgerService.getLedger(id)).thenThrow(new AccountNotFoundException(id));

        mockMvc.perform(get("/api/accounts/{id}/ledger", id)).andExpect(status().isNotFound());
    }
}
