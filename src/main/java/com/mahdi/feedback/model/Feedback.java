package com.mahdi.feedback.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback", indexes = {
        @Index(name = "idx_feedback_service", columnList = "service"),
        @Index(name = "idx_feedback_ts", columnList = "timestamp"),
        @Index(name = "idx_feedback_sentiment", columnList = "sentiment")
})
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, length = 128)
    private String service;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(nullable = false, length = 16)
    private String sentiment;

    @Column(length = 256)
    private String themes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public String getThemes() { return themes; }
    public void setThemes(String themes) { this.themes = themes; }
}
