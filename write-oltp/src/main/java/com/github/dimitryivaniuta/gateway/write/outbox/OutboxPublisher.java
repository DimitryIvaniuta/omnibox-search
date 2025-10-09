package com.github.dimitryivaniuta.gateway.write.outbox;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.github.dimitryivaniuta.gateway.write.repo.OutboxRepo;

import java.util.*;


@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxRepo outbox;
    private final KafkaTemplate<String, String> kafka;


    @Scheduled(fixedDelay = 200, initialDelay = 1000)
    public void publishBatch() {
        var rows = outbox.fetchBatch(500);
        if (rows.isEmpty()) return;
        List<Long> published = new ArrayList<>();
        rows.forEach(r -> {
            String type = (String) r.get("type");
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
            var key = tenant + ":" + entityId;
            kafka.send(new ProducerRecord<>(topic, key, payload));
            published.add(((Number) r.get("id")).longValue());
        });
        outbox.markPublished(published);
    }
}