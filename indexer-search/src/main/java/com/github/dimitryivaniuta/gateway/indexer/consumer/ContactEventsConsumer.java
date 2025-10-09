package com.github.dimitryivaniuta.gateway.indexer.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.gateway.indexer.repo.SearchUpsertRepo;
import java.util.Locale;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for Contact domain events.
 * <p>
 * Consumes events from {@code contact.events.v1} and updates the read-model
 * table {@code search_items} accordingly (upsert on create/update, delete on delete).
 * <p>
 * This component is idempotent when used with a proper unique index
 * (tenant_id, entity_type, entity_id) on {@code search_items}, and relies on
 * Postgres {@code ON CONFLICT} in {@link SearchUpsertRepo}.
 */
@Component
public class ContactEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContactEventsConsumer.class);

    /** Constant entity type label used in the read-model (search_items.entity_type). */
    private static final String ENTITY_TYPE = "CONTACT";

    /** JSON keys used by the producer/outbox payload. */
    private static final String K_TYPE = "type";
    private static final String K_TENANT = "tenantId";
    private static final String K_CONTACT_ID = "contactId";
    private static final String K_TITLE = "title";
    private static final String K_SUBTITLE = "subtitle";
    private static final String K_VISIBLE = "visible";

    /** Jackson ObjectMapper for robust JSON parsing. */
    private final ObjectMapper om;

    /** Repository that performs UPSERT/DELETE into the read-model (search_items). */
    private final SearchUpsertRepo repo;

    public ContactEventsConsumer(ObjectMapper om, SearchUpsertRepo repo) {
        this.om = om;
        this.repo = repo;
    }

    /**
     * Handle a single Contact event record.
     * <p>
     * We intentionally use a single-record listener for broad compatibility.
     * With {@code spring.kafka.listener.ack-mode=BATCH}, commits are still batched
     * by the container; we don't manually acknowledge here.
     *
     * @param record Kafka record containing JSON payload with Contact event.
     */
    @KafkaListener(topics = "contact.events.v1")
    public void onMessage(ConsumerRecord<String, String> record) {
        final String key = record.key();
        final String json = record.value();

        try {
            final JsonNode root = om.readTree(json);

            final String type = textOrEmpty(root, K_TYPE).toUpperCase(Locale.ROOT);
            final String tenantId = textOrEmpty(root, K_TENANT);
            final String contactId = textOrEmpty(root, K_CONTACT_ID);

            // Basic validation before touching the DB
            if (tenantId.isBlank() || contactId.isBlank()) {
                log.warn("Skip Contact event due to missing identifiers (tenantId/contactId). key={}, type={}, payload={}",
                        key, type, redact(json));
                return;
            }

            // Visibility flag: false => treat as delete; true/absent => upsert (for create/update)
            final boolean visible = root.hasNonNull(K_VISIBLE) && root.get(K_VISIBLE).asBoolean();

            switch (type) {
                case "CONTACTCREATED":
                case "CONTACTUPDATED": {
                    if (!visible) {
                        // Respect producer's visibility (soft-deleted on write side)
                        repo.delete(tenantId, ENTITY_TYPE, contactId);
                        log.debug("Deleted CONTACT from search_items (invisible). tenantId={}, contactId={}", tenantId, contactId);
                        return;
                    }
                    final String title = textOrEmpty(root, K_TITLE);
                    final String subtitle = textOrEmpty(root, K_SUBTITLE);
                    if (title.isBlank()) {
                        // Title is required for useful search; skip noisy payloads
                        log.warn("Skip upsert: missing title. tenantId={}, contactId={}, payload={}",
                                tenantId, contactId, redact(json));
                        return;
                    }
                    repo.upsertContact(tenantId, contactId, title, subtitle);
                    log.debug("Upserted CONTACT into search_items. tenantId={}, contactId={}, title={}", tenantId, contactId, title);
                    break;
                }

                case "CONTACTDELETED": {
                    repo.delete(tenantId, ENTITY_TYPE, contactId);
                    log.debug("Deleted CONTACT from search_items. tenantId={}, contactId={}", tenantId, contactId);
                    break;
                }

                default: {
                    // Unknown types are ignored but logged for observability
                    log.info("Ignored Contact event with unsupported type: {} (key={}, tenantId={}, contactId={})",
                            type, key, tenantId, contactId);
                }
            }
        } catch (Exception e) {
            // Defensive: never throw out of the listener; log and continue
            log.error("Failed to process Contact event. key={}, payload={}, error={}",
                    key, redact(json), e.toString(), e);
        }
    }

    /**
     * Safely extract a text value from a JSON node; returns an empty string if missing/null.
     *
     * @param node parent JSON node
     * @param field field name to read
     * @return trimmed text value or empty string
     */
    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        final String v = node.get(field).asText("");
        return v == null ? "" : v.trim();
    }

    /**
     * Redact potentially sensitive values in logs (very light touch here).
     * You can extend this to mask emails/phones if needed.
     *
     * @param s original string
     * @return same string for now; placeholder for future masking
     */
    private static String redact(String s) {
        return s == null ? "" : s;
    }
}
