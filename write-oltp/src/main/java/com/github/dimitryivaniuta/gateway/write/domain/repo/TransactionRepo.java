package com.github.dimitryivaniuta.gateway.write.domain.repo;

import com.github.dimitryivaniuta.gateway.write.domain.Transaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for Transactions (DB-generated UUIDs, optimistic locking).
 */
@Repository
@RequiredArgsConstructor
public class TransactionRepo {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Transaction> RM = new RowMapper<>() {
        @Override
        public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            var delTs = rs.getTimestamp("deleted_at");
            return Transaction.builder().id((UUID) rs.getObject("id")).tenantId(rs.getString("tenant_id")).title(rs.getString("title")).subtitle(rs.getString("subtitle")).amount(rs.getBigDecimal("amount")).currency(rs.getString("currency")).status(rs.getString("status")).version(rs.getLong("version")).createdAt(rs.getTimestamp("created_at").toInstant()).updatedAt(rs.getTimestamp("updated_at").toInstant()).deletedAt(delTs == null ? null : delTs.toInstant()).build();
        }
    };

    /**
     * Insert new Transaction with DB-generated UUID and version=0. @return generated id
     */
    public UUID insertAndReturnId(Transaction t) {
        final String sql = """
                insert into transactions
                  (tenant_id, title, subtitle, amount, currency, status, 
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
            ps.setBigDecimal(4, t.getAmount());
            ps.setString(5, t.getCurrency());
            ps.setString(6, t.getStatus());
            ps.setObject(7, t.getContactId());  // UUID
            ps.setObject(8, t.getListingId());  // UUID
            return ps;
        }, rs -> {
            rs.next();
            return (UUID) rs.getObject(1);
        });
    }

    /**
     * Update Transaction with optimistic locking; returns fresh row.
     */
    public Transaction update(Transaction t, long expectedVersion) {
        int c = jdbc.update("""
                update transactions
                   set title=?, subtitle=?, amount=?, currency=?, status=?,
                       contact_id = coalesce(?, contact_id),
                       listing_id = coalesce(?, listing_id),
                       version=version+1, updated_at=now()
                 where id=? and tenant_id=? and version=? and deleted_at is null
                """, t.getTitle(), t.getSubtitle(), t.getAmount(), t.getCurrency(), t.getStatus(),
                t.getContactId(),
                t.getListingId(),
                t.getId(), t.getTenantId(), expectedVersion);
        if (c == 0) {
            throw new OptimisticLockingFailureException("Transaction version mismatch or deleted: id=" + t.getId());
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

    /**
     * Find by id in tenant.
     */
    public Optional<Transaction> find(String tenant, UUID id) {
        return jdbc.query("select * from transactions where tenant_id=? and id=?", RM, tenant, id).stream()
                .findFirst();
    }
}
