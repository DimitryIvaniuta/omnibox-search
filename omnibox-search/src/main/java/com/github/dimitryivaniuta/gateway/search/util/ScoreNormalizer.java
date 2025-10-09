package com.github.dimitryivaniuta.gateway.search.util;

public final class ScoreNormalizer {
    private ScoreNormalizer() {}
    public static double normalize(double value, double min, double max) {
        if (Double.compare(max, min) == 0) return 0.0d;
        double clamped = Math.max(min, Math.min(max, value));
        return (clamped - min) / (max - min);
    }
}