package com.mahdi.feedback.controller;

import com.mahdi.feedback.model.Feedback;
import com.mahdi.feedback.service.AnalyticsService;
import com.mahdi.feedback.service.InsightsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final AnalyticsService analytics;
    private final InsightsService insights;

    public FeedbackController(AnalyticsService analytics, InsightsService insights) {
        this.analytics = analytics;
        this.insights = insights;
    }

    @GetMapping("/insights")
    public List<InsightsService.Insight> insights() {
        return insights.generate();
    }

    @GetMapping("/services")
    public List<String> services() {
        return analytics.services();
    }

    @GetMapping("/volume-over-time")
    public List<AnalyticsService.DailyVolume> volumeOverTime() {
        return analytics.volumeOverTime();
    }

    @GetMapping("/by-service")
    public List<AnalyticsService.ServiceVolume> byService() {
        return analytics.volumeByService();
    }

    @GetMapping("/themes")
    public List<AnalyticsService.ThemeCount> themes() {
        return analytics.themeBreakdown();
    }

    @GetMapping("/themes-by-service")
    public List<AnalyticsService.ServiceThemeCount> themesByService() {
        return analytics.themesByService();
    }

    @GetMapping("/recent")
    public List<Feedback> recent(@RequestParam(required = false) String sentiment,
                                 @RequestParam(required = false) String service,
                                 @RequestParam(defaultValue = "50") int limit) {
        return analytics.recentFeedback(sentiment, service, Math.min(limit, 500));
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<AnalyticsService.ServiceVolume> byService = analytics.volumeByService();
        List<AnalyticsService.ThemeCount> themes = analytics.themeBreakdown();
        List<AnalyticsService.DailyVolume> daily = analytics.volumeOverTime();

        long totalFeedback = byService.stream().mapToLong(AnalyticsService.ServiceVolume::total).sum();
        long totalNegative = byService.stream().mapToLong(AnalyticsService.ServiceVolume::negative).sum();
        long totalPositive = byService.stream().mapToLong(AnalyticsService.ServiceVolume::positive).sum();

        AnalyticsService.ServiceVolume worstService = byService.stream()
                .max((a, b) -> Double.compare(a.negativeRate(), b.negativeRate()))
                .orElse(null);

        long spikeDays = daily.stream().filter(AnalyticsService.DailyVolume::spike).count();

        return Map.of(
                "totalFeedback", totalFeedback,
                "totalPositive", totalPositive,
                "totalNegative", totalNegative,
                "topTheme", themes.isEmpty() ? null : themes.get(0).theme(),
                "worstService", worstService == null ? null : worstService.service(),
                "worstServiceNegativeRate", worstService == null ? 0.0 : worstService.negativeRate(),
                "spikeDays", spikeDays
        );
    }
}
