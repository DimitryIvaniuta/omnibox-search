package com.github.dimitryivaniuta.gateway.indexer.repo;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Repository for read-model mutations in {@code search_items}.
 * <p>
 * This repo is used by Kafka consumers to reflect domain events (e.g., CONTACT created/updated/deleted)
 * into the denormalized search table. It relies on a unique constraint
 * {@code (tenant_id, entity_type, entity_id)} and PostgreSQL {@code ON CONFLICT} for idempotency.
 * <p>
 * Notes:
 * <ul>
 *   <li>The {@code tsv} column in {@code search_items} is a GENERATED ALWAYS expression from
 *       {@code title/subtitle}, so we don't need to manage it here.</li>
 *   <li>All methods are small single-statement operations; transactions are not required.</li>
 *   <li>Inputs are validated defensively to avoid empty keys being written.</li>
 * </ul>
 */
@Repository
public class SearchUpsertRepo {

    /** Low-level JDBC helper provided by Spring. */
    private final JdbcTemplate jdbc;

    /** Logical entity type label persisted in {@code search_items.entity_type}. */
    private static final String ENTITY_CONTACT = "CONTACT";

    /**
     * Create a new repository.
     *
     * @param jdbc injected {@link JdbcTemplate} configured for the read-model Postgres
     */
    public SearchUpsertRepo(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    }

    /**
     * Upsert a Contact row into {@code search_items}.
     * <p>
     * If the row (tenant, CONTACT, contactId) exists, updates {@code title/subtitle}; otherwise inserts it.
     *
     * @param tenantId   tenant identifier (required, non-blank)
     * @param contactId  source Contact id (required, non-blank)
     * @param title      primary text used for search ranking (required, non-blank)
     * @param subtitle   optional secondary text (nullable/blank allowed)
     * @throws IllegalArgumentException if {@code tenantId}, {@code contactId}, or {@code title} are blank
     */
    public void upsertContact(String tenantId, String contactId, String title, String subtitle) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(contactId)) {
            throw new IllegalArgumentException("contactId must not be blank");
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("title must not be blank");
        }

        // Null subtitle is fine; store as NULL (generated tsvector handles coalesce)
        final String sql = """
                INSERT INTO search_items (tenant_id, entity_type, entity_id, title, subtitle)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, entity_type, entity_id)
                DO UPDATE SET
                  title    = EXCLUDED.title,
                  subtitle = EXCLUDED.subtitle
                """;

        jdbc.update(sql, tenantId, ENTITY_CONTACT, contactId, title, blankToNull(subtitle));
    }

    /**
     * Delete a row from {@code search_items}.
     *
     * @param tenantId   tenant identifier (required, non-blank)
     * @param entityType entity type label as stored in {@code search_items.entity_type} (required, non-blank)
     * @param entityId   source entity id (required, non-blank)
     * @return number of affected rows (0 if nothing was deleted)
     * @throws IllegalArgumentException if any argument is blank
     */
    public int delete(String tenantId, String entityType, String entityId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(entityType)) {
            throw new IllegalArgumentException("entityType must not be blank");
        }
        if (!StringUtils.hasText(entityId)) {
            throw new IllegalArgumentException("entityId must not be blank");
        }

        final String sql = """
                DELETE FROM search_items
                 WHERE tenant_id = ?
                   AND entity_type = ?
                   AND entity_id = ?
                """;

        return jdbc.update(sql, tenantId, entityType, entityId);
    }

    /**
     * Convert blank strings to {@code null}. PostgreSQL distinguishes empty string and NULL,
     * and the generated {@code tsvector} already coalesces NULLs for safe indexing.
     *
     * @param s input string (nullable)
     * @return {@code null} if input is null/blank; otherwise the original string
     */
    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s : null;
    }
}
