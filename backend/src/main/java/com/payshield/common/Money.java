package com.payshield.common;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable monetary value represented strictly in integer minor units (e.g. INR paise).
 * Never uses floating-point arithmetic.
 */
public final class Money {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("INR", "USD", "EUR", "GBP");

    private final long amountMinor;
    private final String currency;

    private Money(long amountMinor, String currency) {
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
            throw new DomainException("INVALID_CURRENCY", "Unsupported ISO-4217 currency: " + currency);
        }
        this.amountMinor = amountMinor;
        this.currency = currency.toUpperCase();
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money positive(long amountMinor, String currency) {
        if (amountMinor <= 0) {
            throw new DomainException("INVALID_AMOUNT", "Monetary amount must be greater than zero minor units");
        }
        return new Money(amountMinor, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(this.amountMinor, other.amountMinor), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(this.amountMinor, other.amountMinor), this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new DomainException("CURRENCY_MISMATCH",
                    "Cannot combine " + this.currency + " with " + other.currency);
        }
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amountMinor == money.amountMinor && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountMinor, currency);
    }

    @Override
    public String toString() {
        return currency + " " + amountMinor + " minor";
    }
}
