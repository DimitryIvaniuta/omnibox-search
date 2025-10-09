package com.github.dimitryivaniuta.gateway.indexer.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class SearchUpsertRepo {
    private final NamedParameterJdbcTemplate jdbc;

    public void upsertBatch(List<Map<String, ?>> rows) {
        if (rows.isEmpty()) return;
        jdbc.batchUpdate("""
                insert into search_items(tenant_id, entity_type, entity_id, title, subtitle)
                values(:tenant, :type, :id, :title, :subtitle)
                on conflict (tenant_id, entity_type, entity_id)
                do update set title=excluded.title, subtitle=excluded.subtitle
                """, rows.toArray(new Map[0]));
    }


    public void delete(String tenant, String type, String id) {
        jdbc.update("delete from search_items where tenant_id=:t and entity_type=:y and entity_id=:i",
                Map.of("t", tenant, "y", type, "i", id));
    }
}