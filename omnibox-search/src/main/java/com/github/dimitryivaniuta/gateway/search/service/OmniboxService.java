package com.github.dimitryivaniuta.gateway.search.service;

import com.github.dimitryivaniuta.gateway.search.graphql.Tokenizer;
import com.github.dimitryivaniuta.gateway.search.graphql.dto.*;
import com.github.dimitryivaniuta.gateway.search.repository.SearchRepository;
import com.github.dimitryivaniuta.gateway.search.security.TenantContextHolder;
import com.github.dimitryivaniuta.gateway.search.util.ScoreNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Omnibox (type-ahead) search across multiple entity types backed by a read-optimized Postgres model.
 * <p>
 * Features:
 * <ul>
 *   <li>Tenant isolation via {@link TenantContextHolder}</li>
 *   <li>Case-insensitive tokenization, prefix tsquery, short-query fallback (ILIKE+trgm)</li>
 *   <li>Score normalization to [0..1] per request</li>
 *   <li>Per-entity capping, deterministic ordering (done in SQL), safe mapping</li>
 *   <li>Micrometer RED metrics and DB timer</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OmniboxService {

    private static final Logger log = LoggerFactory.getLogger(OmniboxService.class);

    /** Max rows fetched from DB before we slice per-group (keeps memory & latency bounded). */
    private static final int HARD_CAP = 200;

    /** Guardrails for client-provided per-group limit. */
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 5;

    /** Short-query threshold: for queries length ≤ this, use ILIKE+trgm fallback for responsiveness. */
    private static final int SHORT_QUERY_LEN = 2;

    /** Entity type labels as persisted in search_items.entity_type (UPPERCASE). */
    private static final String TYPE_CONTACT = "CONTACT";
    private static final String TYPE_LISTING = "LISTING";
    private static final String TYPE_REFERRAL = "REFERRAL";
    private static final String TYPE_TRANSACTION = "TRANSACTION";
    private static final String TYPE_PRODUCT = "PRODUCT";
    private static final String TYPE_MAILING = "MAILING";

    private final SearchRepository repo;
    private final MeterRegistry metrics;

    /**
     * Execute omnibox search.
     *
     * @param q              raw user input
     * @param limitPerGroup  requested cap per entity group
     * @return grouped, normalized results
     */
    public OmniboxResult search(String q, int limitPerGroup) {
        final String tenant = TenantContextHolder.getRequiredTenant();

        // Normalize/guard inputs early
        final String norm = Tokenizer.normalize(q);
        final int perGroup = clamp(limitPerGroup, MIN_LIMIT, MAX_LIMIT, DEFAULT_LIMIT);

        if (norm.isEmpty()) {
            metrics.counter("omnibox.requests.total", "tenant", tenant, "result", "empty").increment();
            return OmniboxResult.builder().build();
        }

        final List<String> toks = Tokenizer.tokens(norm);
        final boolean shortQuery = norm.length() <= SHORT_QUERY_LEN;
        final String prefixTs = Tokenizer.toPrefixTsQuery(toks);
        final String term = norm.toLowerCase(Locale.ROOT);
        final String pattern = "%" + term + "%"; // for ILIKE/trgm fallback branch

        // Observe DB time separately (helps isolate JDBC/PG latency from mapping)
        final Timer.Sample sample = Timer.start(metrics);
        final List<Map<String, Object>> rows;
        try {
            rows = repo.query(tenant, "english", prefixTs, term, pattern, HARD_CAP, shortQuery);
        } catch (Exception e) {
            // Defensive: never fail the UX; emit metric + safe empty response
            metrics.counter("omnibox.requests.total", "tenant", tenant, "result", "error").increment();
            log.error("Omnibox DB query failed. tenant={}, q='{}', shortQuery={}, err={}",
                    tenant, redact(norm), shortQuery, e.toString(), e);
            sample.stop(metrics.timer("omnibox.db.timer", "tenant", tenant, "result", "error"));
            return OmniboxResult.builder().build();
        }
        sample.stop(metrics.timer("omnibox.db.timer", "tenant", tenant, "result", rows.isEmpty() ? "empty" : "ok"));

        if (rows.isEmpty()) {
            metrics.counter("omnibox.requests.total", "tenant", tenant, "result", "empty").increment();
            return OmniboxResult.builder().build();
        }

        // Normalize scores to [0..1] for client-friendly blending
        double max = rows.stream().mapToDouble(r -> ((Number) r.get("score")).doubleValue()).max().orElse(1.0);
        double min = rows.stream().mapToDouble(r -> ((Number) r.get("score")).doubleValue()).min().orElse(0.0);

        // Group by entity_type (kept uppercase consistently from the DB)
        Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>(8);
        for (Map<String, Object> r : rows) {
            final String t = str(r.get("entity_type"));
            byType.computeIfAbsent(t, k -> new ArrayList<>()).add(r);
        }

        // Build response (slice per group)
        OmniboxResult.OmniboxResultBuilder builder = OmniboxResult.builder();

        slice(byType.get(TYPE_CONTACT), perGroup).forEach(r -> builder.contact(toContact(r, min, max)));
        slice(byType.get(TYPE_LISTING), perGroup).forEach(r -> builder.listing(toListing(r, min, max)));
        slice(byType.get(TYPE_REFERRAL), perGroup).forEach(r -> builder.referral(toReferral(r, min, max)));
        slice(byType.get(TYPE_TRANSACTION), perGroup).forEach(r -> builder.transaction(toTransaction(r, min, max)));
        slice(byType.get(TYPE_PRODUCT), perGroup).forEach(r -> builder.product(toProduct(r, min, max)));
        slice(byType.get(TYPE_MAILING), perGroup).forEach(r -> builder.mailing(toMailing(r, min, max)));

        metrics.counter("omnibox.requests.total", "tenant", tenant, "result", "ok").increment();
        return builder.build();
    }

    // --------------------------- Mapping helpers ---------------------------

    private List<Map<String, Object>> slice(List<Map<String, Object>> list, int n) {
        if (list == null || list.isEmpty()) return List.of();
        return list.size() <= n ? list : list.subList(0, n);
    }

    private float normScore(Map<String, Object> r, double min, double max) {
        double raw = ((Number) r.get("score")).doubleValue();
        return (float) ScoreNormalizer.normalize(raw, min, max);
    }

    private SearchHitContact toContact(Map<String, Object> r, double min, double max) {
        final String id = str(r.get("entity_id"));
        return SearchHitContact.builder()
                .id("c_" + id)
                .title(str(r.get("title")))
                .subtitle(optStr(r.get("subtitle")))
                .score(normScore(r, min, max))
                .contactId(id)
                .build();
    }

    private SearchHitListing toListing(Map<String, Object> r, double min, double max) {
        final String id = str(r.get("entity_id"));
        // mlsId may or may not be selected by the repo; read defensively
        final String mlsId = optStr(r.get("mls_id"));
        return SearchHitListing.builder()
                .id("l_" + id)
                .title(str(r.get("title")))
                .subtitle(optStr(r.get("subtitle")))
                .score(normScore(r, min, max))
                .listingId(id)
                .mlsId(mlsId)
                .build();
    }

    private SearchHitReferral toReferral(Map<String, Object> r, double min, double max) {
        final String id = str(r.get("entity_id"));
        return SearchHitReferral.builder()
                .id("r_" + id)
                .title(str(r.get("title")))
                .subtitle(optStr(r.get("subtitle")))
                .score(normScore(r, min, max))
                .referralId(id)
                .build();
    }

    private SearchHitTransaction toTransaction(Map<String, Object> r, double min, double max) {
        final String id = str(r.get("entity_id"));
        return SearchHitTransaction.builder()
                .id("t_" + id)
                .title(str(r.get("title")))
                .subtitle(optStr(r.get("subtitle")))
                .score(normScore(r, min, max))
                .transactionId(id)
                .build();
    }

    private SearchHitProduct toProduct(Map<String, Object> r, double min, double max) {
        final String id = str(r.get("entity_id"));
        return SearchHitProduct.builder()
                .id("p_" + id)
                .title(str(r.get("title")))
                .subtitle(optStr(r.get("subtitle")))
                .score(normScore(r, min, max))
                .productId(id)
                .build();
    }

    private SearchHitMailing toMailing(Map<String, Object> r, double min, double max) {
        final String id = str(r.get("entity_id"));
        return SearchHitMailing.builder()
                .id("m_" + id)
                .title(str(r.get("title")))
                .subtitle(optStr(r.get("subtitle")))
                .score(normScore(r, min, max))
                .mailingId(id)
                .build();
    }

    // --------------------------- Small utils ---------------------------

    private static int clamp(int value, int min, int max, int dflt) {
        if (value < min) return dflt;
        if (value > max) return max;
        return value;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String optStr(Object o) {
        String s = (o == null ? "" : String.valueOf(o));
        return s.isEmpty() ? null : s;
    }

    /**
     * Light-touch redaction for logs (extend to mask emails/phones if needed).
     */
    private static String redact(String s) {
        return s == null ? "" : s;
    }
}
