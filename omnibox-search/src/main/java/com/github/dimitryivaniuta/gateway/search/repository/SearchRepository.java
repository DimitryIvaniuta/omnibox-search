package com.github.dimitryivaniuta.gateway.search.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class SearchRepository {
    private final NamedParameterJdbcTemplate jdbc;


    public List<Map<String, Object>> query(String tenantId,
                                           String cfg,
                                           String prefixTsQuery,
                                           String term,
                                           String likePattern,
                                           int hardCap,
                                           boolean shortQuery) {
        String sql = shortQuery ? SHORT_SQL : FULL_SQL;
        var params = Map.of(
                "tenant", tenantId,
                "cfg", cfg,
                "prefix", prefixTsQuery,
                "term", term,
                "pattern", likePattern,
                "hardCap", hardCap
        );
        return jdbc.queryForList(sql, params);
    }


    private static final String FULL_SQL = """
            with q as (
                select to_tsquery(CAST(:cfg AS regconfig), CAST(:prefix AS text)) as query
            )
            select entity_type,
                entity_id,
                title,
                subtitle,
                ts_rank(tsv, q.query) as fts_score,
                greatest(similarity(lower(title), :term), similarity(lower(subtitle), :term)) as trigram,
                (ts_rank(tsv, q.query) * 0.9 + greatest(0.0, similarity(lower(title), :term)) * 0.3) as score
            from search_items si, q
            where si.tenant_id = :tenant
                and (tsv @@ q.query or lower(title) like :pattern or lower(subtitle) like :pattern)
            order by score desc, entity_id asc
            limit :hardCap
            """;


    private static final String SHORT_SQL = """
            select entity_type,
                entity_id,
                title,
                subtitle,
                0.0 as fts_score,
                greatest(similarity(lower(title), :term), similarity(lower(subtitle), :term)) as trigram,
                greatest(similarity(lower(title), :term), similarity(lower(subtitle), :term)) * 0.3 as score
            from search_items si
            where si.tenant_id = :tenant
                and (lower(title) like :pattern or lower(subtitle) like :pattern)
            order by score desc, entity_id asc
            limit :hardCap
            """;
}