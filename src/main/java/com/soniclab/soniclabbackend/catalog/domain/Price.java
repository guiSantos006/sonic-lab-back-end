package com.soniclab.soniclabbackend.catalog.domain;

import java.math.BigDecimal;

public record Price(BigDecimal amount, String currency) {
    public Price {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O preço não pode ser nulo ou negativo.");
        }
        if (currency == null || currency.isBlank()) {
            currency = "$";
        }
    }
}
