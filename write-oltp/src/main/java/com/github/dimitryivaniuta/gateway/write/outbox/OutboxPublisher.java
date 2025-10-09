package com.github.dimitryivaniuta.gateway.write.outbox;

import com.github.dimitryivaniuta.gateway.write.repo.OutboxRepo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepo outbox;
    private final KafkaTemplate<String, String> kafka;

    /** Polls outbox and publishes in small batches. Idempotent producer is enabled in YAML. */
    @Scheduled(fixedDelay = 200, initialDelay = 1000)
    public void publishBatch() {
        var rows = outbox.fetchBatch(500);
        if (rows.isEmpty()) return;

        List<Long> publishedIds = new ArrayList<>(rows.size());

        for (Map<String, Object> r : rows) {
            String tenant = (String) r.get("tenant_id");
            String aggType = (String) r.get("aggregate_type");
            String entityId = (String) r.get("aggregate_id");
            String payload = r.get("payload").toString();

            String topic = switch (aggType) {
                case "CONTACT" -> "contact.events.v1";
                case "LISTING" -> "listing.events.v1";
                case "REFERRAL" -> "referral.events.v1";
                case "TRANSACTION" -> "transaction.events.v1";
                case "PRODUCT" -> "product.events.v1";
                case "MAILING" -> "mailing.events.v1";
                default -> throw new IllegalArgumentException("Unsupported aggregate_type: " + aggType);
            };

            String key = tenant + ":" + entityId;

            kafka.send(new ProducerRecord<>(topic, key, payload));
            publishedIds.add(((Number) r.get("id")).longValue());
        }

        outbox.markPublished(publishedIds);
    }
}
