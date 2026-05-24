package com.monitor.media.model;

import java.time.Instant;

/**
 * Metadata returned after a file is processed.
 */
public class FileMetadata {

    private String fileId;
    private String originalName;
    private long originalSizeBytes;
    private long processedSizeBytes;
    private String mimeType;
    private String status;
    private long processingTimeMs;
    private Instant uploadedAt;
    private Instant processedAt;
    private String thumbnailUrl;
    private int width;
    private int height;

    private FileMetadata(Builder b) {
        this.fileId = b.fileId;
        this.originalName = b.originalName;
        this.originalSizeBytes = b.originalSizeBytes;
        this.processedSizeBytes = b.processedSizeBytes;
        this.mimeType = b.mimeType;
        this.status = b.status;
        this.processingTimeMs = b.processingTimeMs;
        this.uploadedAt = b.uploadedAt;
        this.processedAt = b.processedAt;
        this.thumbnailUrl = b.thumbnailUrl;
        this.width = b.width;
        this.height = b.height;
    }

    // Getters
    public String getFileId() { return fileId; }
    public String getOriginalName() { return originalName; }
    public long getOriginalSizeBytes() { return originalSizeBytes; }
    public long getProcessedSizeBytes() { return processedSizeBytes; }
    public String getMimeType() { return mimeType; }
    public String getStatus() { return status; }
    public long getProcessingTimeMs() { return processingTimeMs; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String fileId, originalName, mimeType, status, thumbnailUrl;
        private long originalSizeBytes, processedSizeBytes, processingTimeMs;
        private Instant uploadedAt, processedAt;
        private int width, height;

        public Builder fileId(String v) { this.fileId = v; return this; }
        public Builder originalName(String v) { this.originalName = v; return this; }
        public Builder originalSizeBytes(long v) { this.originalSizeBytes = v; return this; }
        public Builder processedSizeBytes(long v) { this.processedSizeBytes = v; return this; }
        public Builder mimeType(String v) { this.mimeType = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder processingTimeMs(long v) { this.processingTimeMs = v; return this; }
        public Builder uploadedAt(Instant v) { this.uploadedAt = v; return this; }
        public Builder processedAt(Instant v) { this.processedAt = v; return this; }
        public Builder thumbnailUrl(String v) { this.thumbnailUrl = v; return this; }
        public Builder width(int v) { this.width = v; return this; }
        public Builder height(int v) { this.height = v; return this; }
        public FileMetadata build() { return new FileMetadata(this); }
    }
}
