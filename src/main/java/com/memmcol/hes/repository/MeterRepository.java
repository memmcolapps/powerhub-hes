package com.memmcol.hes.repository;

import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.model.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<Meter, UUID> {

    // Fetch only meters whose serial numbers are in the given set
    @Query("""
       SELECT new com.memmcol.hes.dto.MeterDTO(
           m.meterNumber,
           mi.model,
           mi.meterClass,
           false,
           m.createdAt
           )
       FROM Meter m
       JOIN m.meterIntegration mi
       WHERE m.meterNumber = :meterNumber
       """)
    Optional<MeterDTO> findMeterDetailsByMeterNumber(@Param("meterNumber") String meterNumber);

    @Query("""
       SELECT new com.memmcol.hes.dto.MeterDTO(
           m.meterNumber,
           mi.model,
           mi.meterClass,
           false,
           m.createdAt
           )
       FROM Meter m
       JOIN m.meterIntegration mi
       WHERE m.meterNumber IN :meterNumbers
       """)
    List<MeterDTO> findMeterDetailsByMeterNumberIn(@Param("meterNumbers") List<String> meterNumbers);

    /*✅ Purpose:
	•	Retrieves all meter numbers with their corresponding models.
	•	Joins meters with meter_integrations.*/
    @Query("""
        SELECT m.meterNumber, mi.model
        FROM Meter m
        JOIN m.meterIntegration mi
    """)
    List<Object[]> findAllMeterModels();

}
