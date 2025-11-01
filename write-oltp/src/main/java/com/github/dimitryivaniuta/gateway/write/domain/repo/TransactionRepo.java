package com.github.dimitryivaniuta.gateway.write.domain.repo;

import com.github.dimitryivaniuta.gateway.money.Money;
import com.github.dimitryivaniuta.gateway.write.domain.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for Transactions (DB-generated UUIDs, optimistic locking).
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TransactionRepo {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Transaction> RM = (rs, i) -> {
        UUID id = rs.getObject("id", UUID.class);
        UUID contactId = rs.getObject("contact_id", UUID.class);
        UUID listingId = rs.getObject("listing_id", UUID.class);
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Timestamp deletedTs = rs.getTimestamp("deleted_at");

        return Transaction.builder()
                .id(id)
                .tenantId(rs.getString("tenant_id"))
                .title(rs.getString("title"))
                .subtitle(rs.getString("subtitle"))
                .total(Money.of(
                        rs.getBigDecimal("total"),
                        rs.getString("currency")
                ))
                .status(rs.getString("status"))
                .contactId(contactId)
                .listingId(listingId)
                .version(rs.getLong("version"))
                .createdAt(createdTs != null ? createdTs.toInstant() : null)
                .updatedAt(updatedTs != null ? updatedTs.toInstant() : null)
                .deletedAt(deletedTs != null ? deletedTs.toInstant() : null)
                .build();
    };

    /**
     * Insert new Transaction with DB-generated UUID and version=0. @return generated id
     */
    public UUID insertAndReturnId(Transaction t) {
        log.info("TX.create tenant={} contactId={} listingId={}", t.getTenantId(), t.getContactId(), t.getListingId());

        // Normalize Money
        var m = t.getTotal() != null ? t.getTotal() : Money.of(BigDecimal.ZERO, "USD");
        var amount = m.amount() == null ? BigDecimal.ZERO : m.amount().setScale(2, RoundingMode.HALF_UP);
        var currency = m.currency() == null ? "USD" : m.currency().trim().toUpperCase(Locale.ROOT);

        final String sql = """
                insert into transactions
                  (tenant_id, title, subtitle, total, currency, status,
                   contact_id, listing_id,
                   version, created_at, updated_at, deleted_at)
                values (?, ?, ?, ?, ?, ?,
                        ?, ?, 0, now(), now(), null)
                returning id
                """;
        return jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setString(1, t.getTenantId());
            ps.setString(2, t.getTitle());
            ps.setString(3, t.getSubtitle());
            ps.setBigDecimal(4, amount);     // total
            ps.setString(5, currency);       // currency
            ps.setString(6, t.getStatus());
            ps.setObject(7, t.getContactId());  // UUID nullable OK
            ps.setObject(8, t.getListingId());  // UUID nullable OK
            return ps;
        }, rs -> {
            rs.next();
            try {
                return rs.getObject(1, UUID.class);
            } catch (Throwable ignore) {
                return UUID.fromString(rs.getString(1));
            }
        });
    }

    /**
     * Offset/limit page (visible only).
     */
    public List<Transaction> findPage(String tenant, int offset, int limit) {
        String sql = """
                select id, tenant_id, title, subtitle,
                       total, currency,
                       status, contact_id, listing_id,
                       version, created_at, updated_at, deleted_at
                  from transactions
                 where tenant_id = ?
                   and deleted_at is null
                 order by id
                 offset ? limit ?
                """;
        return jdbc.query(sql, RM, tenant, offset, limit);
    }

    /**
     * One by id (visible only).
     */
    public Transaction findOne(String tenant, UUID id) {
        List<Transaction> list = jdbc.query("""
                select id, tenant_id, title, subtitle,
                       total, currency,
                       status, contact_id, listing_id,
                       version, created_at, updated_at, deleted_at
                  from transactions
                 where tenant_id = ?
                   and id = ?
                   and deleted_at is null
                """, RM, tenant, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Update Transaction with optimistic locking; returns fresh row.
     */
    public Transaction update(Transaction t, long expectedVersion) {
        // Normalize money if provided (null ⇒ keep existing via COALESCE)
        BigDecimal amount = null;
        String currency = null;
        if (t.getTotal() != null) {
            amount = t.getTotal().amount() == null ? null
                    : t.getTotal().amount().setScale(2, RoundingMode.HALF_UP);
            if (t.getTotal().currency() != null) {
                currency = t.getTotal().currency().trim().toUpperCase(Locale.ROOT);
            }
        }

        int c = jdbc.update("""
                        update transactions
                           set title      = COALESCE(?, title),
                               subtitle   = COALESCE(?, subtitle),
                               total      = COALESCE(?, total),
                               currency   = COALESCE(?, currency),
                               status     = COALESCE(?, status),
                               contact_id = COALESCE(?, contact_id),
                               listing_id = COALESCE(?, listing_id),
                               version    = version + 1,
                               updated_at = now()
                         where id = ? and tenant_id = ? and version = ? and deleted_at is null
                        """,
                t.getTitle(),
                t.getSubtitle(),
                amount,                // may be null → keep old
                currency,              // may be null → keep old
                t.getStatus(),
                t.getContactId(),
                t.getListingId(),
                t.getId(), t.getTenantId(), expectedVersion
        );

        if (c == 0) {
            throw new OptimisticLockingFailureException(
                    "Transaction version mismatch or deleted: id=" + t.getId());
        }
        return find(t.getTenantId(), t.getId()).orElseThrow();
    }


    /**
     * Soft delete by id/version in tenant.
     */
    public void softDelete(String tenant, UUID id, long expectedVersion) {
        int c = jdbc.update("""
                update transactions
                   set deleted_at=now(), version=version+1, updated_at=now()
                 where id=? and tenant_id=? and version=? and deleted_at is null
                """, id, tenant, expectedVersion);
        if (c == 0) {
            throw new OptimisticLockingFailureException("Transaction delete version mismatch or already deleted: id=" + id);
        }
    }

    public List<Transaction> searchByPrefix(String tenant, String q, Integer first) {
        final String term = (q == null ? "" : q.trim());
        if (term.isEmpty()) return List.of();

        final int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
        final String pattern = "%" + term.toLowerCase() + "%";

        final String sql = """
                select id, tenant_id, title, subtitle,
                       total, currency,
                       status, contact_id, listing_id,
                       version, created_at, updated_at, deleted_at
                  from transactions
                 where tenant_id = ?
                   and deleted_at is null
                   and (
                        lower(title)                  like ?
                     or lower(coalesce(subtitle,'')) like ?
                   )
                 order by lower(title) asc, id asc
                 limit ?
                """;

        return jdbc.query(sql, RM, tenant, pattern, pattern, limit);
    }


    /**
     * Find by id in tenant.
     */
    public Optional<Transaction> find(String tenant, UUID id) {
        return jdbc.query("""
                select id, tenant_id, title, subtitle,
                       total, currency,
                       status, contact_id, listing_id,
                       version, created_at, updated_at, deleted_at
                  from transactions
                 where tenant_id = ?
                   and id = ?
                   and deleted_at is null
                """, RM, tenant, id).stream().findFirst();
    }

    /**
     * Bulk soft delete.
     */
    public int[] softDeleteBulk(String tenant, List<UUID> ids) {
        String sql = """
                    update transactions
                    set deleted_at=now(), visible=false, version=version+1
                    where tenant_id=? and id=? and deleted_at is null
                """;
        return jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setString(1, tenant);
                ps.setObject(2, ids.get(i));
            }

            @Override
            public int getBatchSize() {
                return ids.size();
            }
        });
    }

}
