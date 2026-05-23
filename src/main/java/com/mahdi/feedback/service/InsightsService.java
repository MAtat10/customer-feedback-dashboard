package com.mahdi.feedback.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Computes management-facing insights from the analytics aggregates.
 * Each insight is data-driven: pulled from real counts, not hardcoded copy.
 */
@Service
public class InsightsService {

    private final AnalyticsService analytics;

    public InsightsService(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    public record Insight(String category, String headline, String detail, String action) {}

    public List<Insight> generate() {
        List<Insight> out = new ArrayList<>();

        List<AnalyticsService.ServiceVolume> byService = analytics.volumeByService();
        List<AnalyticsService.ThemeCount> themes = analytics.themeBreakdown();
        List<AnalyticsService.ServiceThemeCount> svcThemes = analytics.themesByService();
        List<AnalyticsService.DailyVolume> daily = analytics.volumeOverTime();

        long total = byService.stream().mapToLong(AnalyticsService.ServiceVolume::total).sum();
        long totalNeg = byService.stream().mapToLong(AnalyticsService.ServiceVolume::negative).sum();
        long totalPos = byService.stream().mapToLong(AnalyticsService.ServiceVolume::positive).sum();
        double negShare = total == 0 ? 0 : (double) totalNeg / total;
        double posShare = total == 0 ? 0 : (double) totalPos / total;

        out.add(new Insight(
                "Key pattern",
                String.format("Overall sentiment is %.0f%% positive, %.0f%% negative",
                        posShare * 100, negShare * 100),
                String.format("%,d feedback rows across %d services. Positive feedback dominates, " +
                                "but negative feedback clusters around specific services and themes - see below.",
                        total, byService.size()),
                "Keep doing what's working; focus engineering capacity on the concentrated negative clusters."
        ));

        AnalyticsService.ServiceVolume worst = byService.stream()
                .max(Comparator.comparingDouble(AnalyticsService.ServiceVolume::negativeRate))
                .orElse(null);
        if (worst != null) {
            String topNegTheme = topNegativeThemeFor(worst.service(), svcThemes);
            out.add(new Insight(
                    "Main pain point",
                    String.format("%s is the most at-risk service (%.1f%% negative)",
                            worst.service(), worst.negativeRate() * 100),
                    String.format("%,d feedback rows, %,d negative. Top complaint theme: %s.",
                            worst.total(), worst.negative(), topNegTheme),
                    String.format("Run an end-to-end product audit on %s prioritised by %s.",
                            worst.service(), topNegTheme)
            ));
        }

        AnalyticsService.ThemeCount topNegTheme = themes.stream()
                .filter(t -> !"positive_experience".equals(t.theme()))
                .findFirst().orElse(null);
        if (topNegTheme != null) {
            String topService = topServiceForTheme(topNegTheme.theme(), svcThemes);
            out.add(new Insight(
                    "Main pain point",
                    String.format("'%s' is the #1 recurring complaint (%,d mentions)",
                            topNegTheme.theme(), topNegTheme.count()),
                    String.format("Most-affected service: %s. Issue appears repeatedly across the dataset, " +
                            "suggesting a systemic rather than one-off problem.", topService),
                    String.format("Address %s on %s as the highest-leverage fix.",
                            topNegTheme.theme(), topService)
            ));
        }

        Map<String, Long> themeTotals = themes.stream()
                .filter(t -> !"positive_experience".equals(t.theme()))
                .collect(java.util.stream.Collectors.toMap(
                        AnalyticsService.ThemeCount::theme,
                        AnalyticsService.ThemeCount::count));
        String topNegThemeName = topNegTheme == null ? null : topNegTheme.theme();
        String mostCrossCutting = mostEvenlySpread(svcThemes, themeTotals, byService.size(), topNegThemeName);
        if (mostCrossCutting != null) {
            long themeTotal = themeTotals.getOrDefault(mostCrossCutting, 0L);
            out.add(new Insight(
                    "Key pattern",
                    String.format("'%s' is the most cross-cutting issue (%,d mentions across all services)",
                            mostCrossCutting, themeTotal),
                    "This theme appears in every service with similar weight, suggesting a shared root cause " +
                            "rather than a product-specific issue.",
                    String.format("Invest in shared tooling/process for %s instead of per-product fixes.",
                            mostCrossCutting)
            ));
        }

        long spikes = daily.stream().filter(AnalyticsService.DailyVolume::spike).count();
        if (spikes > 0) {
            String spikeDates = daily.stream()
                    .filter(AnalyticsService.DailyVolume::spike)
                    .map(d -> d.date().toString())
                    .collect(java.util.stream.Collectors.joining(", "));
            out.add(new Insight(
                    "Key pattern",
                    String.format("%d volume spike day%s detected (count >= mean + 2 std dev)",
                            spikes, spikes == 1 ? "" : "s"),
                    "Spike dates: " + spikeDates + ". These often correlate with releases or outages.",
                    "Cross-reference spike dates with deployment timestamps; gate releases behind a canary if confirmed."
            ));
        }

        long outageMentions = themes.stream()
                .filter(t -> "outage".equals(t.theme()))
                .mapToLong(AnalyticsService.ThemeCount::count).findFirst().orElse(0);
        if (outageMentions > 0) {
            long servicesWithOutage = svcThemes.stream()
                    .filter(s -> "outage".equals(s.theme()))
                    .map(AnalyticsService.ServiceThemeCount::service)
                    .distinct().count();
            out.add(new Insight(
                    "Recommended action",
                    String.format("Reliability concern: %,d outage mentions across %d service%s",
                            outageMentions, servicesWithOutage, servicesWithOutage == 1 ? "" : "s"),
                    "Outages are reported across multiple products, pointing at shared infrastructure rather " +
                            "than isolated incidents.",
                    "Publish a status page, review shared infra dependencies, and circulate a monthly SLA report."
            ));
        }

        return out;
    }

    private static String topNegativeThemeFor(String service, List<AnalyticsService.ServiceThemeCount> all) {
        return all.stream()
                .filter(s -> s.service().equals(service))
                .filter(s -> !"positive_experience".equals(s.theme()))
                .max(Comparator.comparingLong(AnalyticsService.ServiceThemeCount::count))
                .map(AnalyticsService.ServiceThemeCount::theme)
                .orElse("none");
    }

    private static String topServiceForTheme(String theme, List<AnalyticsService.ServiceThemeCount> all) {
        return all.stream()
                .filter(s -> s.theme().equals(theme))
                .max(Comparator.comparingLong(AnalyticsService.ServiceThemeCount::count))
                .map(AnalyticsService.ServiceThemeCount::service)
                .orElse("multiple");
    }

    private static String mostEvenlySpread(List<AnalyticsService.ServiceThemeCount> all,
                                           Map<String, Long> themeTotals, int serviceCount,
                                           String exclude) {
        Map<String, Long> minPerTheme = new java.util.HashMap<>();
        Map<String, Integer> spreadPerTheme = new java.util.HashMap<>();
        for (AnalyticsService.ServiceThemeCount r : all) {
            if ("positive_experience".equals(r.theme())) continue;
            if (r.theme().equals(exclude)) continue;
            minPerTheme.merge(r.theme(), r.count(), Math::min);
            spreadPerTheme.merge(r.theme(), 1, Integer::sum);
        }
        return spreadPerTheme.entrySet().stream()
                .filter(e -> e.getValue() == serviceCount)
                .filter(e -> minPerTheme.getOrDefault(e.getKey(), 0L) >= 25)
                .max(Comparator.comparingLong(e -> themeTotals.getOrDefault(e.getKey(), 0L)))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
