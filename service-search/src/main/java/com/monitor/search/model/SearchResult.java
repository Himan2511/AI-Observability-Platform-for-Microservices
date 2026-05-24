package com.monitor.search.model;

import java.util.List;

/**
 * Search result DTO with pagination and performance metadata.
 */
public class SearchResult {

    private final long totalHits;
    private final int page;
    private final int pageSize;
    private final long queryTimeMs;
    private final String queryType;
    private final List<SearchDocument> documents;

    private SearchResult(Builder b) {
        this.totalHits = b.totalHits;
        this.page = b.page;
        this.pageSize = b.pageSize;
        this.queryTimeMs = b.queryTimeMs;
        this.queryType = b.queryType;
        this.documents = b.documents;
    }

    public long getTotalHits() { return totalHits; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getQueryTimeMs() { return queryTimeMs; }
    public String getQueryType() { return queryType; }
    public List<SearchDocument> getDocuments() { return documents; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long totalHits;
        private int page;
        private int pageSize;
        private long queryTimeMs;
        private String queryType;
        private List<SearchDocument> documents;

        public Builder totalHits(long v) { this.totalHits = v; return this; }
        public Builder page(int v) { this.page = v; return this; }
        public Builder pageSize(int v) { this.pageSize = v; return this; }
        public Builder queryTimeMs(long v) { this.queryTimeMs = v; return this; }
        public Builder queryType(String v) { this.queryType = v; return this; }
        public Builder documents(List<SearchDocument> v) { this.documents = v; return this; }
        public SearchResult build() { return new SearchResult(this); }
    }

    public static class SearchDocument {
        private final String id;
        private final String title;
        private final String content;
        private final double score;

        private SearchDocument(DocBuilder b) {
            this.id = b.id;
            this.title = b.title;
            this.content = b.content;
            this.score = b.score;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public double getScore() { return score; }

        public static DocBuilder builder() { return new DocBuilder(); }

        public static class DocBuilder {
            private String id, title, content;
            private double score;

            public DocBuilder id(String v) { this.id = v; return this; }
            public DocBuilder title(String v) { this.title = v; return this; }
            public DocBuilder content(String v) { this.content = v; return this; }
            public DocBuilder score(double v) { this.score = v; return this; }
            public SearchDocument build() { return new SearchDocument(this); }
        }
    }
}
