package com.memmcol.hes.service;

import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.ObisCodeRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.ObjectType;
import gurux.dlms.objects.GXDLMSDemandRegister;
import gurux.dlms.objects.GXDLMSExtendedRegister;
import gurux.dlms.objects.GXDLMSRegister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.memmcol.hesTraining.services.MeterReadingService.getUnitSymbol;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObisScalerService {

    private final DlmsReaderUtils dlmsReaderUtils;
    private final ObisCodeRepository obisCodeRepository;
    private final SessionManagerMultiVendor sessionManagerMultiVendor;

    /**
     * Update scaler & unit for all active OBIS codes of a given meter model
     * Returns JSON report of failures
     */
    public Map<String, Object> updateScalerUnitForMeter(String meterSerial, String model) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;

        List<ObisCodeEntity> obisCodes = obisCodeRepository.findActiveByModel(model);

        if (obisCodes.isEmpty()) {
            log.warn("No active OBIS codes found for meter {} with model {}", meterSerial, model);
            report.put("status", "No OBIS mappings found");
            return report;
        }

        GXDLMSClient client = sessionManagerMultiVendor.getOrCreateClient(meterSerial);
        if (client == null) {
            log.error("No active DLMS session for meter: {}", meterSerial);
            report.put("status", "No active DLMS session");
            return report;
        }

        for (ObisCodeEntity codeEntity : obisCodes) {
            String fullCode = codeEntity.getCode();
            String captureObis = fullCode;
            int classId = 3; // Default to Register

            if (fullCode != null && fullCode.contains(";")) {
                String[] parts = fullCode.split(";");
                if (parts.length >= 2) {
                    try {
                        classId = Integer.parseInt(parts[0].trim());
                    } catch (NumberFormatException ignored) {}
                    captureObis = parts[1].trim();
                }
            }

            try {
                Map<String, Object> scalerUnit = readScalerUnit(
                        client,
                        meterSerial,
                        captureObis,
                        classId
                );

                double scaler = (double) scalerUnit.get("scaler");
                String unit = (String) scalerUnit.get("units");

                if (Arrays.asList("KW", "KVA", "KVar", "KWh", "KVAh", "KVarh").contains(unit)) {
                    scaler = scaler / 1000.0;
                }

                codeEntity.setScaler(String.valueOf(scaler));
                codeEntity.setUnit(unit);
                obisCodeRepository.save(codeEntity);

                success++;

                log.info("Updated {} for meter {}: scaler={}, unit={}", captureObis,
                        meterSerial, scaler, unit);

            } catch (Exception ex) {
                log.error("Failed to read scaler/unit for OBIS {} on meter {}: {}",
                        captureObis, meterSerial, ex.getMessage());
                failures.add(Map.of(
                        "meterSerial", meterSerial,
                        "obisCode", captureObis,
                        "error", ex.getMessage()
                ));
            }
        }

        report.put("status", "Completed");
        report.put("meterSerial", meterSerial);
        report.put("totalMappings", obisCodes.size());
        report.put("success count", success);
        report.put("failures count", failures.size());
        report.put("failures", failures);

        log.info("One-off OBIS scaler/unit update completed for meter: {}", meterSerial);
        return report;
    }

    private Map<String, Object> readScalerUnit(GXDLMSClient client, String meterSerial, String captureObis,
                                               int classId) throws Exception {
        double scaler = 1.0;
        String units = "";

        ObjectType type = ObjectType.forValue(classId);
        switch (type) {
            case REGISTER -> {
                GXDLMSRegister reg = new GXDLMSRegister();
                reg.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, reg, 3);
                scaler = (reg.getScaler() == 0) ? 1.0 : reg.getScaler();
                units = getUnitSymbol(reg.getUnit());
            }
            case DEMAND_REGISTER -> {
                GXDLMSDemandRegister dr = new GXDLMSDemandRegister();
                dr.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 3);
                scaler = (dr.getScaler() == 0) ? 1.0 : dr.getScaler();
                units = getUnitSymbol(dr.getUnit());
            }
            case EXTENDED_REGISTER -> {
                GXDLMSExtendedRegister dr = new GXDLMSExtendedRegister();
                dr.setLogicalName(captureObis);
                dlmsReaderUtils.readScalerUnit(client, meterSerial, dr, 3);
                scaler = (dr.getScaler() == 0) ? 1.0 : dr.getScaler();
                units = getUnitSymbol(dr.getUnit());
            }
            default -> log.warn("Unsupported object type for OBIS: {}, Class ID: {}", captureObis, classId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("captureObis", captureObis);
        response.put("scaler", scaler);
        response.put("units", units);
        return response;
    }

}
