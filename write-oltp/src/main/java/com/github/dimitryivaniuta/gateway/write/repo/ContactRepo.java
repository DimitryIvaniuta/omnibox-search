package com.github.dimitryivaniuta.gateway.write.repo;

import com.github.dimitryivaniuta.gateway.write.domain.Contact;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.*;
import java.util.*;


@Repository
@RequiredArgsConstructor
public class ContactRepo {
    private final JdbcTemplate jdbc;
    private final RowMapper<Contact> RM = (rs, i) -> Contact.builder()
            .id(UUID.fromString(rs.getString("id")))
            .tenantId(rs.getString("tenant_id"))
            .fullName(rs.getString("full_name"))
            .email(rs.getString("email"))
            .phone(rs.getString("phone"))
            .label(rs.getString("label"))
            .version(rs.getLong("version"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .deletedAt(rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant())
            .build();


    public Optional<Contact> find(String tenant, UUID id) {
        return jdbc.query(
                "select * from contacts where tenant_id=? and id=?",
                RM, tenant, id).stream().findFirst();
    }


    public void insert(Contact c) {
        jdbc.update("""
                        insert into contacts(id, tenant_id, full_name, email, phone, label, version, created_at, updated_at, deleted_at)
                        values(?,?,?,?,?,?,?, now(), now(), ?)
                        on conflict (id) do update set
                        full_name=excluded.full_name, email=excluded.email, phone=excluded.phone, label=excluded.label,
                        version=contacts.version+1, updated_at=now(), deleted_at=excluded.deleted_at
                        """, c.getId(), c.getTenantId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getLabel(), c.getVersion(),
                c.getDeletedAt() == null ? null : Timestamp.from(c.getDeletedAt()));
    }


    /** Update with optimistic locking (version check) */
    public Contact update(Contact c, long expectedVersion) {
        int updated = jdbc.update("""
        update contacts
           set full_name = ?,
               email = ?,
               phone = ?,
               label = ?,
               version = version + 1,
               updated_at = now()
         where id = ? and tenant_id = ? and version = ?
        """,
                c.getFullName(), c.getEmail(), c.getPhone(), c.getLabel(),
                c.getId(), c.getTenantId(), expectedVersion
        );
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Contact version mismatch (stale write): id=" + c.getId());
        }
        // return fresh row
        return find(c.getTenantId(), c.getId()).orElseThrow();
    }

    /** Soft delete (set deleted_at) with optimistic locking */
    public void softDelete(String tenant, UUID id, long expectedVersion) {
        int updated = jdbc.update("""
        update contacts
           set deleted_at = now(),
               version = version + 1,
               updated_at = now()
         where id = ? and tenant_id = ? and version = ? and deleted_at is null
        """,
                id, tenant, expectedVersion
        );
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Contact delete version mismatch or already deleted: id=" + id);
        }
    }

    /** Convenience: direct re-activate (not used here, but handy) */
    public void undelete(String tenant, UUID id, long expectedVersion) {
        int updated = jdbc.update("""
        update contacts
           set deleted_at = null,
               version = version + 1,
               updated_at = now()
         where id = ? and tenant_id = ? and version = ?
        """,
                id, tenant, expectedVersion
        );
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Contact undelete version mismatch: id=" + id);
        }
    }

}