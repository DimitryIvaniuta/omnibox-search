package com.github.dimitryivaniuta.gateway.write.domain.repo;

import com.github.dimitryivaniuta.gateway.money.Money;
import com.github.dimitryivaniuta.gateway.write.domain.Listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for Listings.
 * Uses explicit SQL and optimistic locking for predictable OLTP behavior.
 */
@Repository
@RequiredArgsConstructor
public class ListingRepo {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Listing> RM = (rs, n) -> {
        Listing l = new Listing();
        l.setId(java.util.UUID.fromString(rs.getString("id")));
        l.setTenantId(rs.getString("tenant_id"));
        l.setTitle(rs.getString("title"));
        l.setPrice(Money.of(
                rs.getBigDecimal("price"),
                rs.getString("currency")
        ));
        l.setMlsId(rs.getString("mls_id"));
        l.setVersion(rs.getLong("version"));
        var cr = rs.getTimestamp("created_at");
        var up = rs.getTimestamp("updated_at");
        var del = rs.getTimestamp("deleted_at");
        l.setCreatedAt(cr == null ? null : cr.toInstant());
        l.setUpdatedAt(up == null ? null : up.toInstant());
        l.setDeletedAt(del == null ? null : del.toInstant());
        return l;
    };

    /**
     * Find a Listing by id within a tenant.
     */

    public Optional<Listing> find(String tenant, UUID id) {
        return jdbc.query(
                """
                        select id, tenant_id, title, subtitle,
                               price, currency,
                               mls_id,
                               contact_id,
                               version, created_at, updated_at, deleted_at
                        from listings
                        where tenant_id = ? and id = ? and deleted_at is null
                        """,
                (rs, n) -> {
                    var delTs = rs.getTimestamp("deleted_at");
                    var createdTs = rs.getTimestamp("created_at");
                    var updatedTs = rs.getTimestamp("updated_at");
                    UUID contactId = rs.getObject("contact_id", UUID.class);

                    return Listing.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .tenantId(rs.getString("tenant_id"))
                            .title(rs.getString("title"))
                            .subtitle(rs.getString("subtitle"))
                            .mlsId(rs.getString("mls_id"))
                            // Money value object from DECIMAL + ISO currency
                            .price(Money.of(rs.getBigDecimal("price"),
                                    rs.getString("currency")))
                            .contactId(contactId)
                            .version(rs.getLong("version"))
                            .createdAt(createdTs == null ? null : createdTs.toInstant())
                            .updatedAt(updatedTs == null ? null : updatedTs.toInstant())
                            .deletedAt(delTs == null ? null : delTs.toInstant())
                            .build();
                },
                tenant, id
        ).stream().findFirst();
    }

    /**
     * One by id (visible only).
     */
    public Listing findOne(String tenant, UUID id) {
        List<Listing> list = jdbc.query("""
                    select id, tenant_id, title, price, currency, mls_id, label, version, created_at, updated_at, deleted_at
                    from listings
                    where tenant_id=? and id=? and deleted_at is null
                """, RM, tenant, id);
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * Insert new Listing with DB-generated UUID (gen_random_uuid()) and version=0.
     *
     * @return the generated id
     */
    public UUID insert(Listing l) {
        // Hard requirements
        Objects.requireNonNull(l.getTenantId(), "tenantId is required");
        Objects.requireNonNull(l.getTitle(), "title is required");

        // Money normalization (defaults + scale)
        var money = l.getPrice() != null ? l.getPrice()
                : Money.of(new BigDecimal("0.00"), "USD");
        var amount = money.amount().setScale(2, RoundingMode.HALF_UP);
        var currency = money.currency().trim().toUpperCase(Locale.ROOT);

        final String sql = """
                insert into listings (
                    tenant_id,
                    mls_id,
                    title,
                    subtitle,
                    price,
                    currency,
                    version,
                    created_at,
                    contact_id
                )
                values (?, ?, ?, ?, ?, ?, 0, now(), ?)
                returning id
                """;

        return jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            int i = 1;
            ps.setString(i++, l.getTenantId());
            ps.setString(i++, l.getMlsId());                          // nullable OK
            ps.setString(i++, l.getTitle());
            ps.setString(i++, l.getSubtitle());                       // nullable OK
            ps.setBigDecimal(i++, amount);                            // Money.amount (BigDecimal)
            ps.setString(i++, currency);                              // Money.currency (ISO 3-letter)
            ps.setObject(i++, l.getContactId());                      // UUID, nullable OK
            return ps;
        }, rs -> {
            rs.next();
            // Use JDBC typed getter for UUID if driver supports it
            try {
                return rs.getObject(1, UUID.class);
            } catch (Throwable ignore) {
                return UUID.fromString(rs.getString(1));
            }
        });
    }


    /**
     * Update Listing with optimistic locking; returns the fresh row.
     */
    public Listing update(Listing l, long expectedVersion) {
        Objects.requireNonNull(l.getId(), "id is required");
        Objects.requireNonNull(l.getTenantId(), "tenantId is required");
        Objects.requireNonNull(l.getTitle(), "title is required");

        // Normalize Money (if provided)
        BigDecimal amount = null;
        String currency = null;
        if (l.getPrice() != null) {
            amount = l.getPrice().amount() == null
                    ? null
                    : l.getPrice().amount().setScale(2, RoundingMode.HALF_UP);
            if (l.getPrice().currency() != null) {
                currency = l.getPrice().currency().trim().toUpperCase(Locale.ROOT);
            }
        }

        int c = jdbc.update("""
                        update listings
                           set title = ?,
                               subtitle = COALESCE(?, subtitle),
                               mls_id  = COALESCE(?, mls_id),
                               price   = COALESCE(?, price),
                               currency= COALESCE(?, currency),
                               label   = COALESCE(?, label),
                               contact_id = COALESCE(?, contact_id),
                               version = version + 1,
                               updated_at = now()
                         where id = ? and tenant_id = ? and version = ? and deleted_at is null
                        """,
                // SET ...
                l.getTitle(),
                l.getSubtitle(),                 // nullable -> keep old if null
                l.getMlsId(),                    // nullable -> keep old if null
                amount,                          // nullable -> keep old if null
                currency,                        // nullable -> keep old if null
                l.getContactId(),                // nullable -> keep old if null

                // WHERE ...
                l.getId(), l.getTenantId(), expectedVersion
        );

        if (c == 0) {
            throw new OptimisticLockingFailureException(
                    "Listing version mismatch or deleted: id=" + l.getId());
        }
        return find(l.getTenantId(), l.getId()).orElseThrow();
    }

    /**
     * Offset/limit list (visible only).
     */
    public List<Listing> findPage(String tenant, int offset, int limit) {
        String sql = """
                    select id, tenant_id, title, 
                           price, currency, 
                           mls_id, label, version, created_at, updated_at, deleted_at
                    from listings
                    where tenant_id=? and deleted_at is null
                    order by id
                    offset ? limit ?
                """;
        return jdbc.query(sql, RM, tenant, offset, limit);
    }

    /**
     * Soft delete Listing with optimistic locking.
     */
    public void softDelete(String tenant, UUID id, long expectedVersion) {
        int c = jdbc.update("""
                update listings
                   set deleted_at=now(), version=version + 1, updated_at=now()
                 where id=? and tenant_id=? and version=? and deleted_at is null
                """, id, tenant, expectedVersion);
        if (c == 0) {
            throw new OptimisticLockingFailureException(
                    "Listing delete version mismatch or already deleted: id=" + id);
        }
    }

    /**
     * Bulk soft delete.
     */
    public int[] softDeleteBulk(String tenant, List<UUID> ids) {
        String sql = """
                    update listings
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

    public List<Listing> searchByPrefix(String tenant, String q, Integer first) {
        final String term = (q == null ? "" : q.trim());
        if (term.isEmpty()) return List.of();

        final int limit = (first == null || first <= 0 || first > 100) ? 20 : first;
        final String pattern = term.toLowerCase() + "%";

        final String sql = """
                select *
                  from listings
                 where tenant_id = ?
                   and deleted_at is null
                   and (
                        lower(title)              like ?
                     or lower(coalesce(subtitle,'')) like ?
                     or lower(coalesce(mls_id,''))   like ?
                   )
                 order by lower(title) asc, id asc
                 limit ?
                """;

        return jdbc.query(sql, RM, tenant, pattern, pattern, pattern, limit);
    }

    public UUID listingContact(String tenant, UUID listingId) {
        return jdbc.query(con -> {
            var ps = con.prepareStatement(
                    "select contact_id from listings where tenant_id=? and id=? and deleted_at is null");
            ps.setString(1, tenant);
            ps.setObject(2, listingId);
            return ps;
        }, rs -> rs.next() ? (UUID) rs.getObject(1) : null);
    }

    public boolean listingExists(String tenant, UUID listingId) {
        Integer one = jdbc.queryForObject(
                "select 1 from listings where tenant_id=? and id=? and deleted_at is null",
                Integer.class, tenant, listingId
        );
        return one != null;
    }

}
