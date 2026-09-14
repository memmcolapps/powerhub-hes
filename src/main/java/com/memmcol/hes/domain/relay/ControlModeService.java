package com.memmcol.hes.domain.relay;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.ObisCodeRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlModeService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;
    private final ObisCodeRepository obisCodeRepository;
    private final MeterRepository meterRepository;

    private static final String ACTION = "Relay Mode Operation";

    public Map<String, Object> setControlMode(String meterSerial, int mode) throws Exception {

        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);

            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: "+ meterSerial));

            String model = meter.getMeterModel();

            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            List<ObisCodeEntity> obisEntity = obisCodeRepository.findActiveByModelAndAction(model, ACTION);
            if (obisEntity.isEmpty()) {
                throw new IllegalStateException( "No OBIS mapping found for model=" + model+ " action=" + ACTION);
            }

            ObisCodeEntity obis = obisEntity.get(0);

            String[] parts = obis.getCode().split(";");
            if (parts.length < 3) {
                throw new IllegalStateException(
                        "OBIS code '" + obis.getCode() + "' does not match expected format " +
                        "(classId;obisCode;attributeIndex;dataIndex)");
            }

            int classId = Integer.parseInt(parts[0].trim());
            String obisCode = parts[1].trim();
            int attributeIndex = Integer.parseInt(parts[2].trim());

            log.info("Resolved OBIS from DB: classId={}, obisCode={}, attributeIndex={} (model={}, action={})",
                    classId, obisCode, attributeIndex, model, ACTION);

            DlmsResponse response = dlmsReaderUtils.writeAttribute(
                    client,
                    meterSerial,
                    obisCode,
                    classId,
                    attributeIndex,
                    (byte) mode,
                    DataType.ENUM
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", response.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("sentMode", mode);
//            result.put("obisCode", obisCode);
//            result.put("classId", classId);
//            result.put("attributeIndex", attributeIndex);

            if (response.isSuccess()) {
                log.info("Control mode command sent successfully to meter {}", meterSerial);
            } else {
                log.error("Failed to write Mode to meter {}: {} ({})", meterSerial, response.getMessage(), response.getStatus());
            }
            return result;
        });
    }
}
