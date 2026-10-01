package com.atreyamitra.ledgerguard;

import com.atreyamitra.ledgerguard.domain.Account;
import com.atreyamitra.ledgerguard.domain.BalanceOverflowException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountTest {
    @Test void creditAccumulates() {
        Account a = new Account("Asha"); a.credit(5); a.credit(7);
        assertThat(a.getBalance()).isEqualTo(12);
    }
    @Test void creditRejectsNonPositiveAmounts() {
        Account a = new Account("Asha");
        assertThatThrownBy(() -> a.credit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.credit(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getBalance()).isZero();
    }
    @Test void overflowThrowsDomainExceptionAndLeavesBalanceUntouched() {
        Account a = new Account("Asha"); a.credit(Long.MAX_VALUE);
        assertThatThrownBy(() -> a.credit(1)).isInstanceOf(BalanceOverflowException.class);
        assertThat(a.getBalance()).isEqualTo(Long.MAX_VALUE);
    }
}
