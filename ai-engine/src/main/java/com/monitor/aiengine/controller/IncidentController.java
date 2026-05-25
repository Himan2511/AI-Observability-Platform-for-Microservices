package com.monitor.aiengine.controller;

import com.monitor.aiengine.dto.IncidentDetailDto;
import com.monitor.aiengine.entity.AiAnalysisResult;
import com.monitor.aiengine.entity.Incident;
import com.monitor.aiengine.repository.AiAnalysisResultRepository;
import com.monitor.aiengine.repository.IncidentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;

    public IncidentController(IncidentRepository incidentRepository, AiAnalysisResultRepository aiAnalysisResultRepository) {
        this.incidentRepository = incidentRepository;
        this.aiAnalysisResultRepository = aiAnalysisResultRepository;
    }

    @GetMapping
    public Page<Incident> listIncidents(@PageableDefault(sort = "detectedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return incidentRepository.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDetailDto> getIncident(@PathVariable Long id) {
        return incidentRepository.findById(id)
                .map(incident -> {
                    AiAnalysisResult analysis = aiAnalysisResultRepository.findByIncidentId(id).orElse(null);
                    return ResponseEntity.ok(new IncidentDetailDto(incident, analysis));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Incident> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String newStatus = payload.get("status");
        return incidentRepository.findById(id)
                .map(incident -> {
                    incident.setStatus(newStatus);
                    if ("ACKNOWLEDGED".equals(newStatus) && incident.getAcknowledgedAt() == null) {
                        incident.setAcknowledgedAt(OffsetDateTime.now());
                    } else if ("RESOLVED".equals(newStatus) && incident.getResolvedAt() == null) {
                        incident.setResolvedAt(OffsetDateTime.now());
                    }
                    return ResponseEntity.ok(incidentRepository.save(incident));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
