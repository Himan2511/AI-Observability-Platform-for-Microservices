package com.monitor.aiengine.repository;

import com.monitor.aiengine.entity.ServiceRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceRegistryRepository extends JpaRepository<ServiceRegistry, Long> {
    Optional<ServiceRegistry> findByName(String name);
}
