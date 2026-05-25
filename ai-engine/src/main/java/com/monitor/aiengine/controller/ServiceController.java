package com.monitor.aiengine.controller;

import com.monitor.aiengine.entity.ServiceRegistry;
import com.monitor.aiengine.prometheus.MetricCacheService;
import com.monitor.aiengine.prometheus.MetricSnapshot;
import com.monitor.aiengine.repository.ServiceRegistryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceRegistryRepository serviceRegistryRepository;
    private final MetricCacheService metricCacheService;

    public ServiceController(ServiceRegistryRepository serviceRegistryRepository, MetricCacheService metricCacheService) {
        this.serviceRegistryRepository = serviceRegistryRepository;
        this.metricCacheService = metricCacheService;
    }

    @GetMapping
    public List<ServiceRegistry> getAllServices() {
        return serviceRegistryRepository.findAll();
    }

    @GetMapping("/{name}/metrics")
    public ResponseEntity<MetricSnapshot> getServiceMetrics(@PathVariable String name) {
        MetricSnapshot snapshot = metricCacheService.getLatestSnapshot(name);
        if (snapshot != null) {
            return ResponseEntity.ok(snapshot);
        }
        return ResponseEntity.notFound().build();
    }
}
