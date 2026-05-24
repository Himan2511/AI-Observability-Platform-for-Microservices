package com.monitor.recommendation.model;

import java.time.Instant;
import java.util.List;

/**
 * Personalised feed returned by the Recommendation Service.
 */
public class RecommendationFeed {

    private final String userId;
    private final boolean fromCache;
    private final long algorithmDurationMs;
    private final int totalItems;
    private final Instant generatedAt;
    private final List<FeedItem> items;

    private RecommendationFeed(Builder b) {
        this.userId = b.userId;
        this.fromCache = b.fromCache;
        this.algorithmDurationMs = b.algorithmDurationMs;
        this.totalItems = b.totalItems;
        this.generatedAt = b.generatedAt;
        this.items = b.items;
    }

    public String getUserId() { return userId; }
    public boolean isFromCache() { return fromCache; }
    public long getAlgorithmDurationMs() { return algorithmDurationMs; }
    public int getTotalItems() { return totalItems; }
    public Instant getGeneratedAt() { return generatedAt; }
    public List<FeedItem> getItems() { return items; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String userId;
        private boolean fromCache;
        private long algorithmDurationMs;
        private int totalItems;
        private Instant generatedAt;
        private List<FeedItem> items;

        public Builder userId(String v) { this.userId = v; return this; }
        public Builder fromCache(boolean v) { this.fromCache = v; return this; }
        public Builder algorithmDurationMs(long v) { this.algorithmDurationMs = v; return this; }
        public Builder totalItems(int v) { this.totalItems = v; return this; }
        public Builder generatedAt(Instant v) { this.generatedAt = v; return this; }
        public Builder items(List<FeedItem> v) { this.items = v; return this; }
        public RecommendationFeed build() { return new RecommendationFeed(this); }
    }

    public static class FeedItem {
        private final String contentId;
        private final String title;
        private final String category;
        private final double relevanceScore;
        private final double engagementScore;
        private final double finalScore;

        private FeedItem(ItemBuilder b) {
            this.contentId = b.contentId;
            this.title = b.title;
            this.category = b.category;
            this.relevanceScore = b.relevanceScore;
            this.engagementScore = b.engagementScore;
            this.finalScore = b.finalScore;
        }

        public String getContentId() { return contentId; }
        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public double getRelevanceScore() { return relevanceScore; }
        public double getEngagementScore() { return engagementScore; }
        public double getFinalScore() { return finalScore; }

        public static ItemBuilder builder() { return new ItemBuilder(); }

        public static class ItemBuilder {
            private String contentId, title, category;
            private double relevanceScore, engagementScore, finalScore;

            public ItemBuilder contentId(String v) { this.contentId = v; return this; }
            public ItemBuilder title(String v) { this.title = v; return this; }
            public ItemBuilder category(String v) { this.category = v; return this; }
            public ItemBuilder relevanceScore(double v) { this.relevanceScore = v; return this; }
            public ItemBuilder engagementScore(double v) { this.engagementScore = v; return this; }
            public ItemBuilder finalScore(double v) { this.finalScore = v; return this; }
            public FeedItem build() { return new FeedItem(this); }
        }
    }
}
