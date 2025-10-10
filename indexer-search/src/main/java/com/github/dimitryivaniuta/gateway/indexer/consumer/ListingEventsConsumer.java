package com.github.dimitryivaniuta.gateway.indexer.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dimitryivaniuta.gateway.indexer.repo.SearchUpsertRepo;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for Listing domain events.
 *
 * <p>Consumes events from {@code listing.events.v1} and updates the read-model
 * table {@code search_items} accordingly:
 * <ul>
 *   <li>Upsert on ListingCreated / ListingUpdated (when {@code visible=true} or absent)</li>
 *   <li>Delete on ListingDeleted (or when {@code visible=false})</li>
 * </ul>
 *
 * <p>This component is idempotent when used with the unique index
 * {@code (tenant_id, entity_type, entity_id)} on {@code search_items}, and relies on
 * PostgreSQL {@code ON CONFLICT} in {@link SearchUpsertRepo}.
 */
@Component
public class ListingEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ListingEventsConsumer.class);

    /**
     * Constant entity type label used in the read-model (search_items.entity_type).
     */
    private static final String ENTITY_TYPE = "LISTING";

    // ---- JSON keys used by the producer/outbox payload ----
    private static final String K_TYPE = "type";
    private static final String K_TENANT = "tenantId";
    private static final String K_LISTINGID = "listingId";
    private static final String K_TITLE = "title";
    private static final String K_SUBTITLE = "subtitle";
    private static final String K_MLSID = "mlsId";
    private static final String K_VISIBLE = "visible";

    /**
     * Jackson ObjectMapper for robust JSON parsing.
     */
    private final ObjectMapper om;

    /**
     * Repository that performs UPSERT/DELETE into the read-model (search_items).
     */
    private final SearchUpsertRepo repo;

    public ListingEventsConsumer(ObjectMapper om, SearchUpsertRepo repo) {
        this.om = om;
        this.repo = repo;
    }

    /**
     * Handle a single Listing event record.
     *
     * <p>We intentionally use a single-record listener for broad compatibility.
     * With {@code spring.kafka.listener.ack-mode=BATCH}, commits are still batched
     * by the container; we don't manually acknowledge here.
     *
     * @param record Kafka record containing JSON payload with Listing event.
     */
    @KafkaListener(topics = "listing.events.v1")
    public void onMessage(ConsumerRecord<String, String> record) {
        final String key = record.key();
        final String json = record.value();

        // Defensive: ignore Kafka tombstones (null value) gracefully.
        if (json == null) {
            log.info("Ignored tombstone message on listing.events.v1 (key={})", key);
            return;
        }

        try {
            final JsonNode root = om.readTree(json);

            final String type = textOrEmpty(root, K_TYPE).toUpperCase(Locale.ROOT);
            final String tenantId = textOrEmpty(root, K_TENANT);
            final String listingId = textOrEmpty(root, K_LISTINGID);

            // Basic validation before touching the DB
            if (tenantId.isBlank() || listingId.isBlank()) {
                log.warn("Skip Listing event due to missing identifiers (tenantId/listingId). key={}, type={}, payload={}",
                        key, type, redact(json));
                return;
            }

            // Visibility flag: false => treat as delete; true/absent => upsert (for create/update)
            final boolean visible = root.hasNonNull(K_VISIBLE) && root.get(K_VISIBLE).asBoolean();

            switch (type) {
                case "LISTINGCREATED":
                case "LISTINGUPDATED": {
                    if (!visible) {
                        // Respect producer's visibility (soft-deleted on write side)
                        repo.delete(tenantId, ENTITY_TYPE, listingId);
                        log.debug("Deleted LISTING from search_items (invisible). tenantId={}, listingId={}",
                                tenantId, listingId);
                        return;
                    }
                    final String title = textOrEmpty(root, K_TITLE);
                    final String subtitle = textOrEmpty(root, K_SUBTITLE);
                    final String mlsId = textOrEmpty(root, K_MLSID);
                    if (title.isBlank()) {
                        // Title is required for useful search; skip noisy payloads
                        log.warn("Skip upsert: missing title. tenantId={}, listingId={}, payload={}",
                                tenantId, listingId, redact(json));
                        return;
                    }
                    repo.upsertListing(tenantId, listingId, title, normalizeListingSubtitle(subtitle, mlsId));
                    log.debug("Upserted LISTING into search_items. tenantId={}, listingId={}, title={}",
                            tenantId, listingId, title);
                    break;
                }

                case "LISTINGDELETED": {
                    repo.delete(tenantId, ENTITY_TYPE, listingId);
                    log.debug("Deleted LISTING from search_items. tenantId={}, listingId={}",
                            tenantId, listingId);
                    break;
                }

                default: {
                    // Unknown types are ignored but logged for observability
                    log.info("Ignored Listing event with unsupported type: {} (key={}, tenantId={}, listingId={})",
                            type, key, tenantId, listingId);
                }
            }
        } catch (Exception e) {
            // Defensive: never throw out of the listener; log and continue
            log.error("Failed to process Listing event. key={}, payload={}, error={}",
                    key, redact(json), e.toString(), e);
        }
    }

    private String normalizeListingSubtitle(String subtitle, String listingId){
        return (StringUtils.isNotBlank(subtitle) ? subtitle : "")
                + (StringUtils.isNotBlank(listingId) ? listingId : "");
    }


    /**
     * Safely extract a text value from a JSON node; returns an empty string if missing/null.
     *
     * @param node  parent JSON node
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
     * Extend to mask PII if needed.
     *
     * @param s original string
     * @return same string for now; placeholder for future masking
     */
    private static String redact(String s) {
        return s == null ? "" : s;
    }
}
