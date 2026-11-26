package com.payshield.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void addsAndSubtractsMinorUnitsWithoutFloatingPoint() {
        Money a = Money.positive(10050L, "INR");
        Money b = Money.positive(2500L, "INR");
        assertThat(a.add(b).getAmountMinor()).isEqualTo(12550L);
        assertThat(a.subtract(b).getAmountMinor()).isEqualTo(7550L);
    }

    @Test
    void rejectsZeroOrNegativeWhenPositiveRequired() {
        assertThatThrownBy(() -> Money.positive(0L, "INR"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void rejectsCurrencyMismatch() {
        Money inr = Money.positive(1000L, "INR");
        Money usd = Money.positive(1000L, "USD");
        assertThatThrownBy(() -> inr.add(usd))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("CURRENCY_MISMATCH".equals("CURRENCY_MISMATCH") ? "Cannot combine" : "");
    }
}
