package com.mahdi.feedback.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Tags feedback text with one or more themes using keyword/regex buckets.
 * Buckets are intentionally simple and interpretable — easier to defend in a review
 * than a black-box ML model, and good enough to surface management-level patterns.
 */
@Component
public class ThemeClassifier {

    public static final List<String> ALL_THEMES = List.of(
            "support_delay",
            "outage",
            "usability",
            "reporting",
            "onboarding",
            "performance",
            "pricing",
            "positive_experience"
    );

    private static final Map<String, Pattern> BUCKETS = new LinkedHashMap<>();
    static {
        BUCKETS.put("support_delay", compile(
                "support delay", "response time", "slow.*support", "unresolved",
                "waiting for support", "support.*slow", "ticket.*(open|pending)",
                "no response", "took.*days"));
        BUCKETS.put("outage", compile(
                "outage", "downtime", "service down", "unavailable", "crashed?",
                "system failure", "went down", "not working", "disruption"));
        BUCKETS.put("usability", compile(
                "confusing", "hard to use", "difficult to navigate", "user interface",
                "ux", "ui ", "not intuitive", "unintuitive", "cluttered", "complicated"));
        BUCKETS.put("reporting", compile(
                "report", "dashboard.*broken", "missing data", "incorrect data",
                "export", "analytics.*(wrong|missing)", "chart.*(wrong|broken)"));
        BUCKETS.put("onboarding", compile(
                "onboarding", "setup", "getting started", "implementation",
                "initial configuration", "training", "documentation"));
        BUCKETS.put("performance", compile(
                "slow", "lag", "laggy", "performance", "latency", "timeout",
                "freezing", "freezes", "unresponsive", "takes too long"));
        BUCKETS.put("pricing", compile(
                "price", "pricing", "cost", "expensive", "overpriced", "billing",
                "invoice", "subscription"));
        BUCKETS.put("positive_experience", compile(
                "excellent", "professional", "responsive", "smooth", "great",
                "intuitive", "reliable", "highly recommend", "love", "fantastic",
                "improved.*efficiency", "very positive", "exceeded expectations"));
    }

    private static Pattern compile(String... phrases) {
        String joined = String.join("|", phrases);
        return Pattern.compile("(?i)\\b(?:" + joined + ")\\w*");
    }

    public List<String> classify(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : BUCKETS.entrySet()) {
            if (e.getValue().matcher(text).find()) {
                hits.add(e.getKey());
            }
        }
        return hits;
    }

    public String classifyAsCsv(String text) {
        return String.join(",", classify(text));
    }
}
