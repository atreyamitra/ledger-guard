package com.atreyamitra.ledgerguard.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigInteger;
import java.util.*;

@Service
public class ReconciliationService {
    public record Drift(UUID accountId, long balance, BigInteger ledgerTotal) { }
    private final JdbcTemplate jdbc;
    public ReconciliationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(readOnly = true)
    public List<Drift> drifts() {
        // One statement = one PostgreSQL MVCC snapshot, including both balances and entries.
        // SUM(bigint) is numeric in PostgreSQL: do not overflow a Java long while reconciling.
        return jdbc.query("""
                SELECT a.id, a.balance, COALESCE(SUM(e.amount_minor), 0) AS ledger_total
                FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY a.id, a.balance
                HAVING a.balance <> COALESCE(SUM(e.amount_minor), 0)
                ORDER BY a.id
                """, (rs, row) -> new Drift(rs.getObject("id", UUID.class), rs.getLong("balance"),
                        rs.getBigDecimal("ledger_total").toBigIntegerExact()));
    }
}
