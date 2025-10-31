package com.github.dimitryivaniuta.gateway.write.domain.repo;

import com.github.dimitryivaniuta.gateway.write.domain.Listing;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        l.setPrice(rs.getBigDecimal("price")); // never null after migration
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
                "select * from listings where tenant_id=? and id=?",
                (rs, n) -> {
                    var delTs = rs.getTimestamp("deleted_at");
                    var contactId = (UUID) rs.getObject("contact_id");
                    return Listing.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .tenantId(rs.getString("tenant_id"))
                            .title(rs.getString("title"))
                            .subtitle(rs.getString("subtitle"))
                            .version(rs.getLong("version"))
                            .createdAt(rs.getTimestamp("created_at").toInstant())
                            .updatedAt(rs.getTimestamp("updated_at").toInstant())
                            .deletedAt(delTs == null ? null : delTs.toInstant())
                            .contactId(contactId)
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
                    select id, tenant_id, title, price, mls_id, label, version, created_at, updated_at, deleted_at
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
        final String sql = """
                insert into listings (tenant_id, mls_id, title, subtitle, version, 
                                      created_at, updated_at, deleted_at,
                                      contact_id)
                values (?, ?, ?, ?, 0, now(), now(), null, ?)
                 returning id
                """;
        return jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setString(1, l.getTenantId());
            ps.setString(2, l.getMlsId());
            ps.setString(3, l.getTitle());
            ps.setString(4, l.getSubtitle());
            ps.setObject(5, l.getContactId());           // UUID
            return ps;
        }, rs -> {
            rs.next();
            return UUID.fromString(rs.getString(1));
        });
    }

    /**
     * Update Listing with optimistic locking; returns the fresh row.
     */
    public Listing update(Listing l, long expectedVersion) {
        int c = jdbc.update("""
                        update listings
                           set title=?, 
                                subtitle=?, 
                                contact_id = coalesce(?, contact_id),
                                version=version+1, updated_at=now()
                         where id=? and tenant_id=? and version=? and deleted_at is null
                        """,
                l.getTitle(), l.getSubtitle(),
                l.getContactId(),      // may be null → COALESCE keeps old
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
                    select id, tenant_id, title, price, mls_id, label, version, created_at, updated_at, deleted_at
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
                   set deleted_at=now(), version=version+1, updated_at=now()
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
