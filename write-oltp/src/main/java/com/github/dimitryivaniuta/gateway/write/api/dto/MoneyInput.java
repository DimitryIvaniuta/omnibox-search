package com.github.dimitryivaniuta.gateway.write.api.dto;

import java.math.BigDecimal;

public record MoneyInput(BigDecimal amount, String currency) {
}