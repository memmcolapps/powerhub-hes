package com.memmcol.hes.domain.profile;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.model.DlmsResponseStatus;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.ObisCodeRepository;
import gurux.dlms.enums.DataType;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.objects.GXDLMSData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WriteCTPT {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;
    private final MeterRepository meterRepository;
    private final ObisCodeRepository obisCodeRepository;
    private static final String CTPT_ACTION = "CTPT Operation";

    /**
     * Write CT/PT numerator & denominator values into the meter.
     *
     * OBIS / attrIndex:
     * - CT numerator:      1.0.0.4.2.255 (attr 2)
     * - CT denominator:    1.0.0.4.5.255 (attr 2)
     * - PT numerator:      1.0.0.4.3.255 (attr 2)
     * - PT denominator:    1.0.0.4.6.255 (attr 2)
     */
//    public Map<String, Object> writeCtPt(String meterSerial,
//                                         long ctNumerator,
//                                         long ctDenominator,
//                                         long ptNumerator,
//                                         long ptDenominator) throws Exception {
//        return meterLockPort.withExclusive(meterSerial, () -> {
//            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
//            if (client == null) {
//                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
//            }
//
//            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
//                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterSerial));
//
//            String model = meter.getMeterModel();
//
//            List<ObisCodeEntity> obisEntity = obisCodeRepository.findActiveByModelAndAction(model, CTPT_ACTION);
//            if (obisEntity.isEmpty()) {
//                throw new IllegalStateException(
//                        "No OBIS mapping found for model=" + model + " action=" + CTPT_ACTION
//                );
//            }
//
//            ObisCodeEntity obis = obisEntity.get(0);
//
//            String[] parts = obis.getCode().split(";");
//            if (parts.length < 3) {
//                throw new IllegalStateException("OBIS code '" + obis.getCode() + "' does not match expected format " + "(classId;obisCode;attributeIndex;dataIndex)");
//            }
//
//            int classId = Integer.parseInt(parts[0]);
//            String obisCode = parts[1];
//            int attributeId = Integer.parseInt(parts[2]);
//
//            Map<String, Object> result = new LinkedHashMap<>();
//            result.put("meterSerial", meterSerial);
//
//            // NOTE: CT/PT objects are DLMS Data (classId = 1) with DLMS type 'long-unsigned'.
//            // In Gurux Java this corresponds to DataType.UINT16 (16‑bit unsigned).
//
//            // CT numerator: 1.0.0.4.2.255 attr 2
//            DlmsResponse ctNumResp = dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.2.255", 1, 2,
//                    (int) ctNumerator, DataType.UINT16);
//            result.put("ctNumerator", ctNumerator);
//
//            // CT denominator: 1.0.0.4.5.255 attr 2
//            DlmsResponse ctDenResp = dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.5.255", 1, 2,
//                    (int) ctDenominator, DataType.UINT16);
//            result.put("ctDenominator", ctDenominator);
//
//            // PT numerator: 1.0.0.4.3.255 attr 2
//            DlmsResponse ptNumResp = dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.3.255", 1, 2,
//                    (int) ptNumerator, DataType.UINT16);
//            result.put("ptNumerator", ptNumerator);
//
//            // PT denominator: 1.0.0.4.6.255 attr 2
//            DlmsResponse ptDenResp = dlmsReaderUtils.writeAttribute(client, meterSerial, "1.0.0.4.6.255", 1, 2,
//                    (int) ptDenominator, DataType.UINT16);
//            result.put("ptDenominator", ptDenominator);
//
//            boolean allSuccess = ctNumResp.isSuccess() && ctDenResp.isSuccess() && ptNumResp.isSuccess() && ptDenResp.isSuccess();
//
//            result.put("status", allSuccess ? "success" : "failed");
//
//            // If any failed, provide details. In a more complex scenario, we could provide details per OBIS.
//            if (!allSuccess) {
//                if (!ctNumResp.isSuccess()) {
//                    result.put("ctNumeratorError", ctNumResp.getMessage());
//                    result.put("dlmsStatus", ctNumResp.getStatus());
//                } else if (!ctDenResp.isSuccess()) {
//                    result.put("ctDenominatorError", ctDenResp.getMessage());
//                    result.put("dlmsStatus", ctDenResp.getStatus());
//                } else if (!ptNumResp.isSuccess()) {
//                    result.put("ptNumeratorError", ptNumResp.getMessage());
//                    result.put("dlmsStatus", ptNumResp.getStatus());
//                } else if (!ptDenResp.isSuccess()) {
//                    result.put("ptDenominatorError", ptDenResp.getMessage());
//                    result.put("dlmsStatus", ptDenResp.getStatus());
//                }
//                log.error("❌ Failed to write CT/PT to meter {}: {}", meterSerial, result);
//            } else {
//                log.info("✅ CT/PT written successfully meter={} ct={}/{} pt={}/{}",
//                        meterSerial, ctNumerator, ctDenominator, ptNumerator, ptDenominator);
//            }
//
//            return result;
//        });
//    }


    public Map<String, Object> writeCtPt(
            String meterSerial,
            long ctNumerator,
            long ctDenominator,
            long ptNumerator,
            long ptDenominator
    ) throws Exception {

        return meterLockPort.withExclusive(meterSerial, () -> {

            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);

            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            MeterDTO meter = meterRepository
                    .findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Meter not found: " + meterSerial)
                    );

            String model = meter.getMeterModel();

            List<ObisCodeEntity> obisEntities =
                    obisCodeRepository.findActiveByModelAndAction(
                            model,
                            CTPT_ACTION
                    );

            if (obisEntities.size() < 4) {
                throw new IllegalStateException("Expected 4 OBIS mappings for model=" + model + " action=" + CTPT_ACTION + ", but found " + obisEntities.size());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("ctNumerator", ctNumerator);
            result.put("ctDenominator", ctDenominator);
            result.put("ptNumerator", ptNumerator);
            result.put("ptDenominator", ptDenominator);

            try {

                for (ObisCodeEntity obis : obisEntities) {

                    String description = obis.getDescription();

                    if (description == null) {
                        continue;
                    }

                    String desc = description.toLowerCase();

                    if (desc.contains("ct") && desc.contains("numerator")) {

                        DlmsResponse response = writeCtPtValue(
                                client,
                                meterSerial,
                                obis,
                                ctNumerator
                        );

                        if (!isSuccessful(response)) {
                            return buildCtPtFailure(
                                    result,
                                    "ctNumerator",
                                    response
                            );
                        }

                    } else if (desc.contains("ct") && desc.contains("denominator")) {

                        DlmsResponse response = writeCtPtValue(
                                client,
                                meterSerial,
                                obis,
                                ctDenominator
                        );

                        if (!isSuccessful(response)) {
                            return buildCtPtFailure(
                                    result,
                                    "ctDenominator",
                                    response
                            );
                        }

                    } else if (desc.contains("pt") && desc.contains("numerator")) {

                        DlmsResponse response = writeCtPtValue(
                                client,
                                meterSerial,
                                obis,
                                ptNumerator
                        );

                        if (!isSuccessful(response)) {
                            return buildCtPtFailure(
                                    result,
                                    "ptNumerator",
                                    response
                            );
                        }

                    } else if (desc.contains("pt") && desc.contains("denominator")) {

                        DlmsResponse response = writeCtPtValue(
                                client,
                                meterSerial,
                                obis,
                                ptDenominator
                        );

                        if (!isSuccessful(response)) {
                            return buildCtPtFailure(
                                    result,
                                    "ptDenominator",
                                    response
                            );
                        }
                    }
                }

                result.put("status", "success");
                result.put("dlmsStatus", "SUCCESS");
                result.put("message", "CT/PT written successfully.");

                log.info(
                        "CT/PT written successfully meter={} ct={}/{} pt={}/{}",
                        meterSerial,
                        ctNumerator,
                        ctDenominator,
                        ptNumerator,
                        ptDenominator
                );

                return result;

            } catch (Exception e) {

                result.put("status", "failed");
                result.put("dlmsStatus", "COMMUNICATION_ERROR");
                result.put("message","Error writing CT/PT: " + e.getMessage());

                log.error("Exception while writing CT/PT to meter {}",meterSerial,e);

                return result;
            }
        });
    }

    private DlmsResponse writeCtPtValue(
            GXDLMSClient client,
            String meterSerial,
            ObisCodeEntity obis,
            long value
    ) throws Exception {

        String[] parts = obis.getCode().split(";");

        if (parts.length < 3) {
            throw new IllegalStateException(
                    "Invalid OBIS mapping: " + obis.getCode() + ". Expected format: " + "(classId;obisCode;attributeIndex)");
        }

        int classId = Integer.parseInt(parts[0]);
        String obisCode = parts[1];
        int attributeId = Integer.parseInt(parts[2]);

        log.info(
                "Writing CT/PT value={} meter={} classId={} obis={} attribute={}",
                value,
                meterSerial,
                classId,
                obisCode,
                attributeId
        );

        return dlmsReaderUtils.writeAttribute(
                client,
                meterSerial,
                obisCode,
                classId,
                attributeId,
                (int) value,
                DataType.UINT16
        );
    }

    private boolean isSuccessful(DlmsResponse response) {
        return response != null && (response.isSuccess() || "SUCCESS".equalsIgnoreCase(String.valueOf(response.getStatus())));
    }

    private Map<String, Object> buildCtPtFailure(
            Map<String, Object> result,
            String field,
            DlmsResponse response
    ) {
        result.put("status", "failed");
        result.put(field + "Error", response != null ? response.getMessage() : "No response from meter");
        result.put("dlmsStatus", response != null ? response.getStatus() : "UNKNOWN_ERROR");

        log.error("❌ CT/PT write failed: {}", result);

        return result;
    }
}

