package com.monitor.media.service;

import com.monitor.media.model.FileMetadata;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MeterRegistry meterRegistry;
    private final KafkaProducerService kafkaProducerService;

    @Value("${media.concurrent-jobs-threshold:10}")
    private int concurrentJobsThreshold;

    @Value("${media.processing-simulation-ms:500}")
    private long processingSimulationMs;

    private final Map<String, FileMetadata> fileStore     = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobs                = new AtomicInteger(0);
    private final List<byte[]> leakedMemory               = new CopyOnWriteArrayList<>();

    private Counter uploadsCounter;
    private DistributionSummary fileSizeHistogram;

    public MediaService(MeterRegistry meterRegistry, KafkaProducerService kafkaProducerService) {
        this.meterRegistry = meterRegistry;
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostConstruct
    public void init() {
        uploadsCounter = Counter.builder("media.uploads.total")
                .description("Total number of file uploads processed").register(meterRegistry);
        Gauge.builder("media.processing.active", activeJobs, AtomicInteger::get)
                .description("Number of concurrently active media processing jobs").register(meterRegistry);
        fileSizeHistogram = DistributionSummary.builder("media.file.size.bytes")
                .description("Distribution of uploaded file sizes in bytes").baseUnit("bytes")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99).register(meterRegistry);
        log.info("MediaService initialised");
    }

    @Timed(value = "media.processing.duration", description = "Time taken to process a media file")
    public FileMetadata processUpload(MultipartFile file) throws InterruptedException {
        String fileId = UUID.randomUUID().toString();
        long fileSizeBytes = file.getSize() > 0 ? file.getSize() : 1024 * 1024;

        uploadsCounter.increment();
        fileSizeHistogram.record(fileSizeBytes);

        int currentJobs = activeJobs.incrementAndGet();
        log.info("Processing upload: fileId={}, size={}KB, activeJobs={}", fileId, fileSizeBytes / 1024, currentJobs);

        if (currentJobs > concurrentJobsThreshold) {
            kafkaProducerService.publishConcurrentJobOverflow(currentJobs);
        }

        try {
            long start = System.currentTimeMillis();
            Thread.sleep(processingSimulationMs / 3);  // resize
            Thread.sleep(processingSimulationMs / 3);  // thumbnail
            Thread.sleep(processingSimulationMs / 3);  // compression
            long durationMs = System.currentTimeMillis() - start;

            FileMetadata metadata = FileMetadata.builder()
                    .fileId(fileId)
                    .originalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload")
                    .originalSizeBytes(fileSizeBytes).processedSizeBytes((long) (fileSizeBytes * 0.7))
                    .mimeType(file.getContentType() != null ? file.getContentType() : "image/jpeg")
                    .status("COMPLETED").processingTimeMs(durationMs)
                    .uploadedAt(Instant.now()).processedAt(Instant.now())
                    .thumbnailUrl("/media/" + fileId + "/thumbnail")
                    .width(1920).height(1080).build();

            fileStore.put(fileId, metadata);
            log.info("Upload processed: fileId={}, durationMs={}", fileId, durationMs);
            return metadata;
        } finally {
            activeJobs.decrementAndGet();
        }
    }

    public Optional<FileMetadata> getFile(String fileId) {
        return Optional.ofNullable(fileStore.get(fileId));
    }

    public boolean deleteFile(String fileId) {
        return fileStore.remove(fileId) != null;
    }

    public Map<String, Object> getActiveJobsInfo() {
        return Map.of(
                "activeJobs", activeJobs.get(),
                "totalFilesProcessed", fileStore.size(),
                "leakedMemoryMB", leakedMemory.stream().mapToLong(a -> a.length).sum() / (1024 * 1024)
        );
    }

    public Map<String, Object> simulateLargeUpload() throws InterruptedException {
        int sizeMB = 500;
        log.warn("[SIMULATE] Large upload simulation — allocating {}MB", sizeMB);
        uploadsCounter.increment();
        activeJobs.incrementAndGet();
        try {
            byte[] largeFile = new byte[sizeMB * 1024 * 1024];
            Arrays.fill(largeFile, (byte) 1);
            fileSizeHistogram.record((long) sizeMB * 1024 * 1024);
            Thread.sleep(processingSimulationMs);
            return Map.of("simulation", "LARGE_UPLOAD", "allocatedMB", sizeMB, "message", "Large file simulation complete");
        } finally {
            activeJobs.decrementAndGet();
        }
    }

    public Map<String, Object> simulateConcurrentJobs() throws InterruptedException {
        int numJobs = 20;
        log.warn("[SIMULATE] Spawning {} concurrent processing jobs", numJobs);
        CountDownLatch latch = new CountDownLatch(numJobs);
        ExecutorService pool = Executors.newFixedThreadPool(numJobs);
        try {
            for (int i = 0; i < numJobs; i++) {
                pool.submit(() -> {
                    activeJobs.incrementAndGet();
                    int current = activeJobs.get();
                    if (current > concurrentJobsThreshold) { kafkaProducerService.publishConcurrentJobOverflow(current); }
                    try {
                        uploadsCounter.increment();
                        fileSizeHistogram.record(1024 * 1024 * (long) (Math.random() * 50 + 1));
                        Thread.sleep(2000);
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { activeJobs.decrementAndGet(); latch.countDown(); }
                });
            }
            latch.await(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        log.warn("[SIMULATE] Concurrent jobs simulation complete");
        return Map.of("simulation", "CONCURRENT_JOBS", "jobsSpawned", numJobs, "message", "Concurrent jobs simulation complete");
    }

    public Map<String, Object> simulateMemoryLeak() {
        int chunkMB = 50;
        log.warn("[SIMULATE] Memory leak simulation — allocating {}MB without releasing", chunkMB);
        byte[] leakChunk = new byte[chunkMB * 1024 * 1024];
        Arrays.fill(leakChunk, (byte) 42);
        leakedMemory.add(leakChunk);
        long totalLeakedMB = leakedMemory.stream().mapToLong(a -> a.length).sum() / (1024 * 1024);
        log.warn("[SIMULATE] Total leaked memory so far: {}MB", totalLeakedMB);
        if (totalLeakedMB >= 100) { kafkaProducerService.publishMemoryLeakDetected(totalLeakedMB); }
        return Map.of("simulation", "MEMORY_LEAK", "thisAllocationMB", chunkMB, "totalLeakedMB", totalLeakedMB,
                "message", "Memory allocated without release. Call repeatedly to accumulate leaks.");
    }
}
