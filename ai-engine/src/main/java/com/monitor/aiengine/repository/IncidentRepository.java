package com.monitor.aiengine.repository;

import com.monitor.aiengine.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByStatus(String status);
    List<Incident> findByServiceNameAndStatus(String serviceName, String status);
    Optional<Incident> findTopByServiceNameAndAnomalyTypeAndStatusOrderByDetectedAtDesc(String serviceName, String anomalyType, String status);
}
