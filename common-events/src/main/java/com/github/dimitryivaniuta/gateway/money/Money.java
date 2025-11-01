package com.github.dimitryivaniuta.gateway.money;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * BigDecimal, not null at DB, keep precision/scale hints
 * ISO code, 3 letters, uppercase
 */
public final class Money {
    private static final Pattern ISO = Pattern.compile("^[A-Z]{3}$");

    private final BigDecimal amount;
    private final String currency; // ISO-4217

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        if (amount.scale() > 2) amount = amount.setScale(2, BigDecimal.ROUND_HALF_UP);
        if (currency == null || currency.isBlank()) currency = "USD";
        if (!ISO.matcher(currency).matches()) {
            throw new IllegalArgumentException("Invalid currency: " + currency);
        }
        return new Money(amount, currency.toUpperCase());
    }

    public static Money zero(String currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return amount.compareTo(m.amount) == 0 && Objects.equals(currency, m.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}


