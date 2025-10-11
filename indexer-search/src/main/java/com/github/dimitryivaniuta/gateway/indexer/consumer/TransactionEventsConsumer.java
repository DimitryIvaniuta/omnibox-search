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
 * Kafka consumer for Transaction domain events from {@code transaction.events.v1}.
 * Upserts or deletes rows in {@code search_items} based on event type and visibility.
 */
@Component
public class TransactionEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventsConsumer.class);

    private static final String ENTITY_TYPE = "TRANSACTION";
    private static final String K_TYPE = "type";
    private static final String K_TENANT = "tenantId";
    private static final String K_TXN_ID = "transactionId";
    private static final String K_TITLE = "title";
    private static final String K_SUBTITLE = "subtitle";
    private static final String K_VISIBLE = "visible";

    private final ObjectMapper om;
    private final SearchUpsertRepo repo;

    public TransactionEventsConsumer(ObjectMapper om, SearchUpsertRepo repo) {
        this.om = om;
        this.repo = repo;
    }

    @KafkaListener(topics = "transaction.events.v1")
    public void onMessage(ConsumerRecord<String, String> record) {
        final String key = record.key();
        final String json = record.value();
        if (json == null) {
            log.info("Ignored tombstone on transaction.events.v1 (key={})", key);
            return;
        }
        try {
            final JsonNode root = om.readTree(json);

            final String type = textOrEmpty(root, K_TYPE).toUpperCase(Locale.ROOT);
            final String tenantId = textOrEmpty(root, K_TENANT);
            final String txnId = textOrEmpty(root, K_TXN_ID);

            if (tenantId.isBlank() || txnId.isBlank()) {
                log.warn("Skip Transaction event (missing tenant/transaction id). key={}, type={}, payload={}",
                        key, type, redact(json));
                return;
            }

            final boolean visible = root.hasNonNull(K_VISIBLE) && root.get(K_VISIBLE).asBoolean();

            switch (type) {
                case "TRANSACTIONCREATED":
                case "TRANSACTIONUPDATED": {
                    if (!visible) {
                        repo.delete(tenantId, ENTITY_TYPE, txnId);
                        log.debug("Deleted TRANSACTION (invisible). tenantId={}, transactionId={}", tenantId, txnId);
                        return;
                    }
                    final String title = textOrEmpty(root, K_TITLE);
                    final String subtitle = textOrEmpty(root, K_SUBTITLE);
                    if (title.isBlank()) {
                        log.warn("Skip upsert: missing title. tenantId={}, transactionId={}, payload={}",
                                tenantId, txnId, redact(json));
                        return;
                    }
                    repo.upsertTransaction(tenantId, txnId, title, subtitle);
                    log.debug("Upserted TRANSACTION. tenantId={}, transactionId={}, title={}", tenantId, txnId, title);
                    break;
                }
                case "TRANSACTIONDELETED": {
                    repo.delete(tenantId, ENTITY_TYPE, txnId);
                    log.debug("Deleted TRANSACTION. tenantId={}, transactionId={}", tenantId, txnId);
                    break;
                }
                default:
                    log.info("Ignored Transaction event type: {} (key={})", type, key);
            }
        } catch (Exception e) {
            log.error("Failed processing Transaction event. key={}, payload={}, error={}",
                    key, redact(json), e.toString(), e);
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        String v = node.get(field).asText("");
        return v == null ? "" : v.trim();
    }

    private static String redact(String s) {
        return s == null ? "" : s;
    }
}
