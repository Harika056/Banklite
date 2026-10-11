package com.banklite.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String ownerName,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal initialDeposit) {
}
