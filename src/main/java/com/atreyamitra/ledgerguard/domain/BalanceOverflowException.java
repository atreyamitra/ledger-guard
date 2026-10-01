package com.atreyamitra.ledgerguard.domain;

/** Crediting would push a balance past {@link Long#MAX_VALUE} minor units. */
public class BalanceOverflowException extends RuntimeException {
    public BalanceOverflowException(String message, Throwable cause) { super(message, cause); }
}
