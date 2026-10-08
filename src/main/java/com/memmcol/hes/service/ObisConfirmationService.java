package com.memmcol.hes.service;

import com.memmcol.hes.model.Meter;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.ObisCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObisConfirmationService {

    private final MeterRepository meterRepository;
    private final ObisCodeRepository obisCodeRepository;
    private final ProfileMetadataService profileMetadataService;

    /**
     * Executes OBIS code confirmation by performing a live profile generic read on the specified meter.
     * Upserts profile metadata and updates hierarchical confirmation status on the obis_codes table.
     *
     * @param meterIdentifier meter UUID string or meter serial number
     * @return Map containing confirmation summary and status
     */
    public Map<String, Object> confirmObisCodes(String meterIdentifier) {
        log.info("Starting OBIS code confirmation process for meter identifier: {}", meterIdentifier);

        Meter meter = findMeter(meterIdentifier);
        if (meter == null) {
            throw new IllegalArgumentException("Meter not found for identifier: " + meterIdentifier);
        }

        String meterSerial = meter.getMeterNumber();
        if (meter.getMeterIntegration() == null) {
            throw new IllegalStateException("Meter " + meterSerial + " is not associated with any meter integration");
        }

        String meterModel = meter.getMeterIntegration().getModel();
        List<ObisCodeEntity> obisEntities = obisCodeRepository.findActiveByModel(meterModel);

        if (obisEntities.isEmpty()) {
            throw new IllegalStateException("No active OBIS codes configured for meter model: " + meterModel);
        }

        List<ObisCodeEntity> profileObisEntities = obisEntities.stream()
                .filter(o -> "PROFILE".equalsIgnoreCase(o.getObisType()) || "PROFILE_GENERIC".equalsIgnoreCase(o.getObisType()))
                .toList();

        List<ObisCodeEntity> targetObisList = profileObisEntities.isEmpty() ? obisEntities : profileObisEntities;

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("meterId", meter.getId());
        responseMap.put("meterSerial", meterSerial);
        responseMap.put("meterModel", meterModel);

        boolean allSuccessful = true;
        List<Map<String, Object>> profileResults = new ArrayList<>();

        for (ObisCodeEntity obisEntity : targetObisList) {
            String profileObis = obisEntity.getCode();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("obisCode", profileObis);
            result.put("action", obisEntity.getAction());

            try {
                log.info("Triggering live profile metadata read for meter {} (model: {}) on OBIS {}",
                        meterSerial, meterModel, profileObis);

                List<ModelProfileMetadata> metadata = profileMetadataService.loadFromMeterAndPersist(
                        meterSerial, meterModel, profileObis);

                if (metadata != null && !metadata.isEmpty()) {
                    updateStatusInNewTx(obisEntity.getId(), "CONFIRMED", "SUCCESS");
                    result.put("status", "CONFIRMED");
                    result.put("capturedObjectsCount", metadata.size());
                    result.put("response", "SUCCESS");
                } else {
                    String errorMsg = "Failed to load capture objects from meter " + meterSerial;
                    updateStatusInNewTx(obisEntity.getId(), "FAILED", errorMsg);
                    result.put("status", "FAILED");
                    result.put("response", errorMsg);
                    allSuccessful = false;
                }
            } catch (Exception ex) {
                log.error("Error confirming OBIS {} for meter {}: {}", profileObis, meterSerial, ex.getMessage(), ex);
                String errorMsg = ex.getMessage() != null ? ex.getMessage() : "Execution failure during device communication";
                updateStatusInNewTx(obisEntity.getId(), "FAILED", errorMsg);
                result.put("status", "FAILED");
                result.put("response", errorMsg);
                allSuccessful = false;
            }

            profileResults.add(result);
        }

        responseMap.put("overallStatus", allSuccessful ? "CONFIRMED" : "FAILED");
        responseMap.put("details", profileResults);
        return responseMap;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatusInNewTx(UUID obisCodeId, String confirmationStatus, String responseMessage) {
        obisCodeRepository.findById(obisCodeId).ifPresent(obisEntity -> {
            obisEntity.setConfirmation(confirmationStatus);
            obisEntity.setResponse(responseMessage);
            obisCodeRepository.save(obisEntity);
            log.info("Updated ObisCodeEntity {} status to {} with response: {}", obisCodeId, confirmationStatus, responseMessage);
        });
    }

    private Meter findMeter(String meterIdentifier) {
        try {
            UUID id = UUID.fromString(meterIdentifier);
            Optional<Meter> optionalMeter = meterRepository.findById(id);
            if (optionalMeter.isPresent()) {
                return optionalMeter.get();
            }
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, lookup by serial number
        }

        return meterRepository.findByMeterNumber(meterIdentifier).orElse(null);
    }
}
