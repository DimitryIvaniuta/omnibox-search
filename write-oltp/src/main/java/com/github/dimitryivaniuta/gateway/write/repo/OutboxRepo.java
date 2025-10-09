package com.github.dimitryivaniuta.gateway.write.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class OutboxRepo {

    private final NamedParameterJdbcTemplate jdbc;

    /** Insert a new outbox event row (payload must be valid JSON string). */
    public void add(String tenant, String aggregateType, String aggregateId, String type, String payloadJson) {
        jdbc.update("""
        insert into outbox_events(tenant_id, aggregate_type, aggregate_id, type, payload)
        values(:t, :a, :i, :y, CAST(:p AS jsonb))
        """,
                Map.of("t", tenant, "a", aggregateType, "i", aggregateId, "y", type, "p", payloadJson)
        );
    }

    /** Fetch a batch of unpublished rows (oldest first). */
    public List<Map<String, Object>> fetchBatch(int limit) {
        return jdbc.queryForList("""
        select id, tenant_id, aggregate_type, aggregate_id, type, payload
          from outbox_events
         where published = false
         order by id asc
         limit :l
        """,
                Map.of("l", limit)
        );
    }

    /** Mark outbox rows as published. */
    public void markPublished(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        jdbc.update("""
        update outbox_events
           set published = true, published_at = now()
         where id in (:ids)
        """,
                Map.of("ids", ids)
        );
    }
}