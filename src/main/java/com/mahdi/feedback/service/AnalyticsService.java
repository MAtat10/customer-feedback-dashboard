package com.mahdi.feedback.service;

import com.mahdi.feedback.model.Feedback;
import com.mahdi.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final FeedbackRepository repository;

    public AnalyticsService(FeedbackRepository repository) {
        this.repository = repository;
    }

    public record DailyVolume(LocalDate date, long count, boolean spike) {}

    public record ServiceVolume(String service, long total, long positive, long negative, long neutral, double negativeRate) {}

    public record ThemeCount(String theme, long count) {}

    public record ServiceThemeCount(String service, String theme, long count) {}

    /**
     * Daily volume with spike flags. A day is flagged as a spike if its count
     * is at least mean + 2 * stdDev across all days in the dataset.
     */
    public List<DailyVolume> volumeOverTime() {
        List<Object[]> raw = repository.volumePerDay();
        if (raw.isEmpty()) return List.of();

        List<long[]> counts = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            LocalDate d = toLocalDate(r[0]);
            long c = ((Number) r[1]).longValue();
            counts.add(new long[]{d.toEpochDay(), c});
        }

        double mean = counts.stream().mapToLong(a -> a[1]).average().orElse(0);
        double variance = counts.stream().mapToDouble(a -> Math.pow(a[1] - mean, 2)).sum() / counts.size();
        double stdDev = Math.sqrt(variance);
        double threshold = mean + 2 * stdDev;

        List<DailyVolume> out = new ArrayList<>(counts.size());
        for (long[] a : counts) {
            out.add(new DailyVolume(LocalDate.ofEpochDay(a[0]), a[1], a[1] >= threshold && a[1] > mean));
        }
        return out;
    }

    public List<ServiceVolume> volumeByService() {
        List<Object[]> raw = repository.volumeByService();
        List<ServiceVolume> out = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            String service = (String) r[0];
            long positive = ((Number) r[1]).longValue();
            long negative = ((Number) r[2]).longValue();
            long neutral  = ((Number) r[3]).longValue();
            long total    = ((Number) r[4]).longValue();
            double negRate = total == 0 ? 0 : (double) negative / total;
            out.add(new ServiceVolume(service, total, positive, negative, neutral, negRate));
        }
        return out;
    }

    /** Theme counts across the entire dataset. */
    public List<ThemeCount> themeBreakdown() {
        Map<String, Long> counts = new HashMap<>();
        for (Feedback f : repository.findAll()) {
            if (f.getThemes() == null || f.getThemes().isBlank()) continue;
            for (String t : f.getThemes().split(",")) {
                counts.merge(t, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new ThemeCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** Theme counts grouped by service — drives the heatmap. */
    public List<ServiceThemeCount> themesByService() {
        Map<String, Map<String, Long>> grouped = new HashMap<>();
        for (Feedback f : repository.findAll()) {
            if (f.getThemes() == null || f.getThemes().isBlank()) continue;
            Map<String, Long> svc = grouped.computeIfAbsent(f.getService(), k -> new HashMap<>());
            for (String t : f.getThemes().split(",")) {
                svc.merge(t, 1L, Long::sum);
            }
        }
        List<ServiceThemeCount> out = new ArrayList<>();
        grouped.forEach((service, themes) ->
                themes.forEach((theme, count) -> out.add(new ServiceThemeCount(service, theme, count))));
        return out;
    }

    public List<Feedback> recentFeedback(String sentiment, String service, int limit) {
        return repository.findAll().stream()
                .filter(f -> sentiment == null || f.getSentiment().equalsIgnoreCase(sentiment))
                .filter(f -> service == null || f.getService().equalsIgnoreCase(service))
                .sorted(Comparator.comparing(Feedback::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<String> services() {
        return repository.findDistinctServices();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof Date d) return d.toLocalDate();
        if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return LocalDate.parse(o.toString());
    }
}
