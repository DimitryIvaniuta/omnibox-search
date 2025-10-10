package com.github.dimitryivaniuta.gateway.write.domain.repo;

import com.github.dimitryivaniuta.gateway.write.domain.Listing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
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

    private static final RowMapper<Listing> RM = new RowMapper<>() {
        @Override
        public Listing mapRow(ResultSet rs, int rowNum) throws SQLException {
            Instant del = rs.getTimestamp("deleted_at") != null
                    ? rs.getTimestamp("deleted_at").toInstant() : null;
            return Listing.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .tenantId(rs.getString("tenant_id"))
                    .title(rs.getString("title"))
                    .subtitle(rs.getString("subtitle"))
                    .version(rs.getLong("version"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .updatedAt(rs.getTimestamp("updated_at").toInstant())
                    .deletedAt(del)
                    .build();
        }
    };

    /**
     * Find a Listing by id within a tenant.
     */
    public Optional<Listing> find(String tenant, UUID id) {
        return jdbc.query(
                "select * from listings where tenant_id=? and id=?", RM, tenant, id
        ).stream().findFirst();
    }

    /**
     * Insert new Listing with DB-generated UUID (gen_random_uuid()) and version=0.
     *
     * @return the generated id
     */
    public UUID insert(Listing l) {
        final String sql = """
                insert into listings (tenant_id, mls_id, title, subtitle, version, created_at, updated_at, deleted_at)
                values (?, ?, ?, ?, 0, now(), now(), null)
                 returning id
                """;
        return jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setString(1, l.getTenantId());
            ps.setString(2, l.getMlsId());
            ps.setString(3, l.getTitle());
            ps.setString(4, l.getSubtitle());
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
                           set title=?, subtitle=?, version=version+1, updated_at=now()
                         where id=? and tenant_id=? and version=? and deleted_at is null
                        """,
                l.getTitle(), l.getSubtitle(), l.getId(), l.getTenantId(), expectedVersion
        );
        if (c == 0) {
            throw new OptimisticLockingFailureException(
                    "Listing version mismatch or deleted: id=" + l.getId());
        }
        return find(l.getTenantId(), l.getId()).orElseThrow();
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
}
