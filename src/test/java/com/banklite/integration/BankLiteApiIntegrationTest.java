package com.banklite.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.banklite.entity.Account;
import com.banklite.repository.AccountRepository;
import com.banklite.repository.LedgerEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankLiteApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @BeforeEach
    void clean() {
        ledgerRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
    }

    private UUID createAccount(String owner, String deposit) throws Exception {
        String json = mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"" + owner + "\",\"initialDeposit\":" + deposit + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return UUID.fromString(node.get("id").asText());
    }

    private ResultActions transfer(UUID from, UUID to, String amount, String key) throws Exception {
        var request = post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":%s}",
                        from, to, amount));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private BigDecimal balance(UUID id) {
        return accountRepository.findById(id).map(Account::getBalance).orElseThrow();
    }

    @Test
    void createAndFetchAccount() throws Exception {
        UUID id = createAccount("Ada", "250.50");

        mockMvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Ada"))
                .andExpect(jsonPath("$.balance").value(250.5));
    }

    @Test
    void transfer_updatesBalancesAndLedger() throws Exception {
        UUID a = createAccount("A", "100.00");
        UUID b = createAccount("B", "0.00");

        transfer(a, b, "40.00", null).andExpect(status().isCreated());

        assertThat(balance(a)).isEqualByComparingTo("60.00");
        assertThat(balance(b)).isEqualByComparingTo("40.00");
        assertThat(ledgerRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/accounts/{id}/ledger", a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/accounts/{id}/ledger", b))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void transfer_withSameIdempotencyKey_isAppliedOnce() throws Exception {
        UUID a = createAccount("A", "100.00");
        UUID b = createAccount("B", "0.00");

        String first = transfer(a, b, "30.00", "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = transfer(a, b, "30.00", "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("id").asText())
                .isEqualTo(objectMapper.readTree(first).get("id").asText());
        assertThat(balance(a)).isEqualByComparingTo("70.00");
        assertThat(balance(b)).isEqualByComparingTo("30.00");
        assertThat(ledgerRepository.count()).isEqualTo(1);
    }

    @Test
    void transfer_reusingKeyWithDifferentAmount_returns409() throws Exception {
        UUID a = createAccount("A", "100.00");
        UUID b = createAccount("B", "0.00");
        transfer(a, b, "30.00", "key-1").andExpect(status().isCreated());

        transfer(a, b, "31.00", "key-1").andExpect(status().isConflict());

        assertThat(balance(a)).isEqualByComparingTo("70.00");
    }

    @Test
    void transfer_insufficientFunds_returns422AndChangesNothing() throws Exception {
        UUID a = createAccount("A", "10.00");
        UUID b = createAccount("B", "0.00");

        transfer(a, b, "10.01", null).andExpect(status().isUnprocessableEntity());

        assertThat(balance(a)).isEqualByComparingTo("10.00");
        assertThat(balance(b)).isEqualByComparingTo("0.00");
        assertThat(ledgerRepository.count()).isZero();
    }

    @Test
    void transfer_unknownAccount_returns404() throws Exception {
        UUID a = createAccount("A", "10.00");

        transfer(a, UUID.randomUUID(), "1.00", null).andExpect(status().isNotFound());
    }

    @Test
    void transfer_sameAccount_returns400() throws Exception {
        UUID a = createAccount("A", "10.00");

        transfer(a, a, "1.00", null).andExpect(status().isBadRequest());
    }

    @Test
    void getAccount_unknown_returns404() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void ledger_unknownAccount_returns404() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/ledger", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
