package com.memmcol.hes.repository;

import com.memmcol.hes.model.ObisCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ObisCodeRepository extends JpaRepository<ObisCodeEntity, java.util.UUID> {

    @Query("""
        SELECT o FROM ObisCodeEntity o
        JOIN o.meterIntegration mi
        WHERE UPPER(o.action) = UPPER(:action)
          AND UPPER(mi.model) = UPPER(:model)
          AND o.status = 'ACTIVE'
    """)
    List<ObisCodeEntity> findActiveByModelAndAction(
            @Param("model") String model,
            @Param("action") String action
    );
}
