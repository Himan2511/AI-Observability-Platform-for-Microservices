package com.monitor.media.controller;

import com.monitor.media.model.FileMetadata;
import com.monitor.media.service.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/media")
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);
    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileMetadata> upload(@RequestParam(value = "file", required = false) MultipartFile file) throws InterruptedException {
        if (file == null) {
            log.info("POST /media/upload — no file provided, using stub");
            return ResponseEntity.ok(FileMetadata.builder()
                    .fileId("stub-" + System.currentTimeMillis()).originalName("test.jpg")
                    .originalSizeBytes(1024 * 1024).processedSizeBytes(700 * 1024)
                    .mimeType("image/jpeg").status("COMPLETED").processingTimeMs(450).build());
        }
        log.info("POST /media/upload — file={}, size={}KB", file.getOriginalFilename(), file.getSize() / 1024);
        return ResponseEntity.ok(mediaService.processUpload(file));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<FileMetadata> getFile(@PathVariable String fileId) {
        return mediaService.getFile(fileId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs/active")
    public ResponseEntity<Map<String, Object>> getActiveJobs() {
        return ResponseEntity.ok(mediaService.getActiveJobsInfo());
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileId) {
        boolean deleted = mediaService.deleteFile(fileId);
        return deleted ? ResponseEntity.ok(Map.of("deleted", true, "fileId", fileId)) : ResponseEntity.notFound().build();
    }

    @PostMapping("/simulate/large-upload")
    public ResponseEntity<Map<String, Object>> simulateLargeUpload() throws InterruptedException {
        return ResponseEntity.ok(mediaService.simulateLargeUpload());
    }

    @PostMapping("/simulate/concurrent-jobs")
    public ResponseEntity<Map<String, Object>> simulateConcurrentJobs() throws InterruptedException {
        return ResponseEntity.ok(mediaService.simulateConcurrentJobs());
    }

    @PostMapping("/simulate/memory-leak")
    public ResponseEntity<Map<String, Object>> simulateMemoryLeak() {
        return ResponseEntity.ok(mediaService.simulateMemoryLeak());
    }
}
