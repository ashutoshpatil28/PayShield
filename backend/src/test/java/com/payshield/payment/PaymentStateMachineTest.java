package com.payshield.payment;

import com.payshield.common.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentStateMachineTest {

    @Test
    void permitsValidTransitions() {
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.CREATED, PaymentStatus.PROCESSING)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.PROCESSING, PaymentStatus.UNKNOWN)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.UNKNOWN, PaymentStatus.SUCCEEDED)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.UNKNOWN, PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStateMachine.canTransition(PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDED)).isTrue();
    }

    @Test
    void rejectsTerminalToActiveTransitions() {
        assertThatThrownBy(() -> PaymentStateMachine.assertTransition(PaymentStatus.FAILED, PaymentStatus.SUCCEEDED))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Illegal payment state transition");

        assertThatThrownBy(() -> PaymentStateMachine.assertTransition(PaymentStatus.CREATED, PaymentStatus.SUCCEEDED))
                .isInstanceOf(DomainException.class);
    }
}
