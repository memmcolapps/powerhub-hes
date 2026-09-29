package com.memmcol.hes.repository;

import com.memmcol.hes.model.MeterIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterIntegrationRepository extends JpaRepository<MeterIntegration, UUID> {
    Optional<MeterIntegration> findByModelIgnoreCase(String model);
}
