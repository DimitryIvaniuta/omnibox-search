package com.github.dimitryivaniuta.gateway.search.graphql;

import java.util.Arrays;
import java.util.List;

public final class Tokenizer {
    private Tokenizer() {}

    public static String normalize(String q) {
        return q == null ? "" : q.strip().replaceAll("\\s+", " ");
    }

    public static List<String> tokens(String q) {
        var n = normalize(q);
        if (n.isEmpty()) return List.of();
        return Arrays.stream(n.split(" "))
                .filter(t -> !t.isBlank())
                .toList();
    }

    /** Build prefix tsquery like: sam gal -> 'sam:* & gal:*' */
    public static String toPrefixTsQuery(List<String> toks) {
        if (toks.isEmpty()) return "";
        return String.join(" & ", toks.stream().map(t -> t + ":*").toList());
    }
}