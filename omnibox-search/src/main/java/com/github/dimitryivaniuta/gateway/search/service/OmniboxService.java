package com.github.dimitryivaniuta.gateway.search.service;

import com.github.dimitryivaniuta.gateway.search.graphql.Tokenizer;
import com.github.dimitryivaniuta.gateway.search.graphql.dto.*;
import com.github.dimitryivaniuta.gateway.search.repository.SearchRepository;
import com.github.dimitryivaniuta.gateway.search.security.TenantContextHolder;
import com.github.dimitryivaniuta.gateway.search.util.ScoreNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.*;


@Service
@RequiredArgsConstructor
public class OmniboxService {
    private final SearchRepository repo;
    private final MeterRegistry metrics;


    private static final int HARD_CAP = 200; // cap total before per-group slicing


    public OmniboxResult search(String q, int limitPerGroup) {
        String tenant = TenantContextHolder.getRequiredTenant();
        String norm = Tokenizer.normalize(q);
        if (norm.isEmpty()) {
            return OmniboxResult.builder().build();
        }
        var toks = Tokenizer.tokens(norm);
        boolean shortQuery = norm.length() <= 2;


        String prefixTs = Tokenizer.toPrefixTsQuery(toks);
        String term = norm.toLowerCase();
        String pattern = "%" + term + "%"; // for ILIKE path


        Timer.Sample sample = Timer.start(metrics);
        List<Map<String, Object>> rows = repo.query(tenant, "english", prefixTs, term, pattern, HARD_CAP, shortQuery);
        sample.stop(metrics.timer("omnibox.db.timer"));


        if (rows.isEmpty()) return OmniboxResult.builder().build();


// normalize scores to [0..1]
        double max = rows.stream().mapToDouble(r -> ((Number) r.get("score")).doubleValue()).max().orElse(1.0);
        double min = rows.stream().mapToDouble(r -> ((Number) r.get("score")).doubleValue()).min().orElse(0.0);


        Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();
        rows.forEach(r -> byType.computeIfAbsent((String) r.get("entity_type"), k -> new ArrayList<>()).add(r));


        OmniboxResult.OmniboxResultBuilder builder = OmniboxResult.builder();


        slice(byType.get("CONTACT"), limitPerGroup).forEach(r -> builder.contact(toContact(r, min, max)));
        slice(byType.get("LISTING"), limitPerGroup).forEach(r -> builder.listing(toListing(r, min, max)));
        slice(byType.get("REFERRAL"), limitPerGroup).forEach(r -> builder.referral(toReferral(r, min, max)));
        slice(byType.get("TRANSACTION"), limitPerGroup).forEach(r -> builder.transaction(toTransaction(r, min, max)));
        slice(byType.get("PRODUCT"), limitPerGroup).forEach(r -> builder.product(toProduct(r, min, max)));
        slice(byType.get("MAILING"), limitPerGroup).forEach(r -> builder.mailing(toMailing(r, min, max)));


        return builder.build();
    }


    private List<Map<String, Object>> slice(List<Map<String, Object>> list, int n) {
        if (list == null || list.isEmpty()) return List.of();
        return list.size() <= n ? list : list.subList(0, n);
    }


    private float normScore(Map<String, Object> r, double min, double max) {
        double raw = ((Number) r.get("score")).doubleValue();
        return (float) ScoreNormalizer.normalize(raw, min, max);
    }


    private SearchHitContact toContact(Map<String, Object> r, double min, double max) {
        return SearchHitContact.builder()
                .id("c_" + r.get("entity_id"))
                .title((String) r.get("title"))
                .subtitle((String) r.get("subtitle"))
                .score(normScore(r, min, max))
                .contactId((String) r.get("entity_id"))
                .build();
    }

    private SearchHitListing toListing(Map<String, Object> r, double min, double max) {
        return SearchHitListing.builder()
                .id("l_" + r.get("entity_id"))
                .title((String) r.get("title"))
                .subtitle((String) r.get("subtitle"))
                .score(normScore(r, min, max))
                .listingId((String) r.get("entity_id"))
                .build();
    }

    private SearchHitReferral toReferral(Map<String, Object> r, double min, double max) {
        return SearchHitReferral.builder()
                .id("r_" + r.get("entity_id"))
                .title((String) r.get("title"))
                .subtitle((String) r.get("subtitle"))
                .score(normScore(r, min, max))
                .referralId((String) r.get("entity_id"))
                .build();
    }

    private SearchHitTransaction toTransaction(Map<String, Object> r, double min, double max) {
        return SearchHitTransaction.builder()
                .id("t_" + r.get("entity_id"))
                .title((String) r.get("title"))
                .subtitle((String) r.get("subtitle"))
                .score(normScore(r, min, max))
                .transactionId((String) r.get("entity_id"))
                .build();
    }

    private SearchHitProduct toProduct(Map<String, Object> r, double min, double max) {
        return SearchHitProduct.builder()
                .id("p_" + r.get("entity_id"))
                .title((String) r.get("title"))
                .subtitle((String) r.get("subtitle"))
                .score(normScore(r, min, max))
                .productId((String) r.get("entity_id"))
                .build();
    }

    private SearchHitMailing toMailing(Map<String, Object> r, double min, double max) {
        return SearchHitMailing.builder()
                .id("m_" + r.get("entity_id"))
                .title((String) r.get("title"))
                .subtitle((String) r.get("subtitle"))
                .score(normScore(r, min, max))
                .mailingId((String) r.get("entity_id"))
                .build();
    }
}