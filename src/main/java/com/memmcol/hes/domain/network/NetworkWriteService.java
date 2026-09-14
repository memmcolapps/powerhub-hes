package com.memmcol.hes.domain.network;

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
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.GXDLMSIp4Setup;
import gurux.dlms.objects.GXDLMSTcpUdpSetup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to handle network configuration write operations for meters.
 * Following senior engineering practices for DLMS communication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkWriteService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;
    private final MeterRepository meterRepository;
    private final ObisCodeRepository obisCodeRepository;
    private static  final String APN_ACTION = "APN Operation";
    private static  final String IP_PORT_ACTION = "IP/Port Operation";

    /**
     * Write APN value into the meter.
     * MD: OBIS: 0.0.25.4.0.255 (Class 45 - GPRS Setup, Attribute 2 - APN)
     * Non-MD: OBIS: 0.11.25.4.0.255 (Class 45 - GPRS Setup, Attribute 2 - APN)
     */
    public Map<String, Object> writeApn(String meterSerial, String apn) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);

            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterSerial));

            String model = meter.getMeterModel();

//            boolean isMd = "MD".equalsIgnoreCase(meter.getMeterClass());
//            String obis = isMd ? "0.0.25.4.0.255" : "0.11.25.4.0.255";

            List<ObisCodeEntity> obisEntity = obisCodeRepository.findActiveByModelAndAction(model, APN_ACTION);
            if (obisEntity.isEmpty()) {
                throw new IllegalStateException(
                        "No OBIS mapping found for model=" + model + " action=" + APN_ACTION
                );
            }

            ObisCodeEntity obis = obisEntity.get(0);

            String[] parts = obis.getCode().split(";");
            if (parts.length < 3) {
                throw new IllegalStateException(
                        "OBIS code '" + obis.getCode() + "' does not match expected format " +
                                "(classId;obisCode;attributeIndex;dataIndex)");
            }

            int classId = Integer.parseInt(parts[0]);
            String obisCode = parts[1];
            int attributeId = Integer.parseInt(parts[2]);


            log.info("Writing APN '{}' to meter {} (Class: {}, OBIS: {})", apn, meterSerial, meter.getMeterClass(), obisCode);

            // Class 45 (GPRS Setup), Attribute 2 (APN) is Octet String (DataType.OCTET_STRING)
            DlmsResponse response = dlmsReaderUtils.writeAttribute(client, meterSerial, obisCode, classId, attributeId,
                    apn.getBytes(StandardCharsets.UTF_8), DataType.OCTET_STRING);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", response.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("apn", apn);

            if (response.isSuccess()) {
                log.info("✅ APN written successfully to meter {}", meterSerial);
            } else {
                log.error("❌ Failed to write APN to meter {}: {} ({})", meterSerial, response.getMessage(), response.getStatus());
            }
            return result;
        });
    }

    /**
     * Write IP Address and Port into the meter.
     * MD: OBIS: 0.0.2.1.0.255 (Class 29 - Auto Connect, Attribute 6 - Destination List)
     * Non-MD:
     * - Port: OBIS: 0.11.25.0.0.255 (Class 41 - TCP/UDP Setup, Attribute 2 - Port)
     * - IP: OBIS: 0.11.25.4.0.255 (Class 45 - GPRS Setup, Attribute 5 - IP Address)
     */
    public Map<String, Object> writeIpPortV1(String meterSerial, List<String> ipPorts) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterSerial));
            boolean isMd = "MD".equalsIgnoreCase(meter.getMeterClass());

            DlmsResponse response;
            if (isMd) {
                log.info("Writing Destination List {} to MD meter {}", ipPorts, meterSerial);

                // Class 29 (Auto Connect), Attribute 6 (Destination List) is an Array of Octet Strings
                List<byte[]> octetStrings = ipPorts.stream()
                        .map(s -> s.getBytes(StandardCharsets.UTF_8))
                        .toList();

                response = dlmsReaderUtils.writeAttribute(client, meterSerial, "0.0.2.1.0.255", 29, 6,
                        octetStrings, DataType.ARRAY);
            } else {
                // Non-MD logic: Expecting "IP:Port" format in the first element of ipPorts
                String ipPortStr = ipPorts.get(0);
                String[] parts = ipPortStr.split(":");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid IP:Port format for Non-MD meter: " + ipPortStr);
                }
                String ip = parts[0];
                String portStr = parts[1]; // Keep as string for string-based port configuration

                log.info("Writing Port {} then IP {} to Non-MD meter {}", portStr, ip, meterSerial);

                // Fixed: Convert port string to raw UTF-8/ASCII bytes and use DataType.OCTET_STRING (Tag 0x09)
                byte[] portBytes = portStr.getBytes(StandardCharsets.UTF_8);
                response = dlmsReaderUtils.writeAttribute(client, meterSerial, "0.11.25.0.0.255", 41, 2,
                        portBytes, DataType.OCTET_STRING);

                if (response.isSuccess()) {
                    response = dlmsReaderUtils.writeAttribute(client, meterSerial, "0.11.25.4.0.255", 45, 5,
                            ip.getBytes(StandardCharsets.UTF_8), DataType.OCTET_STRING);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("status", response.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("ipPorts", ipPorts);

            if (response.isSuccess()) {
                log.info("✅ Destination List written successfully to meter {}", meterSerial);
            } else {
                log.error("❌ Failed to write Destination List to meter {}: {} ({})", meterSerial, response.getMessage(), response.getStatus());
            }
            return result;
        });
    }

    public Map<String, Object> writeIpPort(String meterSerial, List<String> ipPorts) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterSerial));

            String model = meter.getMeterModel();
//            boolean isMd = "MD".equalsIgnoreCase(meter.getMeterClass());

            List<ObisCodeEntity> obisEntity = obisCodeRepository.findActiveByModelAndAction(model, IP_PORT_ACTION);
            if (obisEntity.isEmpty()) {
                throw new IllegalStateException(
                        "No OBIS mapping found for model=" + model + " action=" + IP_PORT_ACTION
                );
            }

            DlmsResponse response = null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("ipPorts", ipPorts);

            try {
                if (obisEntity.size() == 1) {

                    ObisCodeEntity obis = obisEntity.get(0);
                    String[] parts = obis.getCode().split(";");
                    int classId = Integer.parseInt(parts[0]);
                    String code = parts[1];
                    int attributeId = Integer.parseInt(parts[2]);

                    log.info("Writing Destination List {} to MD meter {}", ipPorts, meterSerial);

                    List<byte[]> octetStrings = ipPorts.stream()
                            .map(s -> s.getBytes(StandardCharsets.UTF_8))
                            .toList();

                    response = dlmsReaderUtils.writeAttribute(client, meterSerial, code, classId, attributeId,
                            octetStrings, DataType.ARRAY);
                } else {
                    if (ipPorts == null || ipPorts.isEmpty()) {
                        throw new IllegalArgumentException("At least one IP:Port value is required");
                    }

                    String ipPort = ipPorts.get(0);

                    String[] ipPortParts = ipPort.split(":", 2);

                    if (ipPortParts.length != 2) {
                        throw new IllegalArgumentException("Invalid IP:Port format: " + ipPort);
                    }

                    String ip = ipPortParts[0];
                    String port = ipPortParts[1];

                    /*
                     * First mapping = IP
                     */
                    ObisCodeEntity ipObis = obisEntity.get(0);

                    String[] ipParts = parseObisMapping(ipObis);

                    int ipClassId = Integer.parseInt(ipParts[0]);
                    String ipCode = ipParts[1];
                    int ipAttributeId = Integer.parseInt(ipParts[2]);

                    log.info(
                            "Writing IP {} to meter {} " + "(Class: {}, OBIS: {}, Attribute: {})",
                            ip,
                            meterSerial,
                            ipClassId,
                            ipCode,
                            ipAttributeId
                    );

                    response = dlmsReaderUtils.writeAttribute(
                            client,
                            meterSerial,
                            ipCode,
                            ipClassId,
                            ipAttributeId,
                            ip.getBytes(StandardCharsets.UTF_8),
                            DataType.OCTET_STRING
                    );

                    /*
                     * Only write Port when IP write succeeds.
                     */
                    if (isSuccessful(response)) {
                        /*
                         * Second mapping = Port
                         */
                        ObisCodeEntity portObis = obisEntity.get(1);

                        String[] portParts = parseObisMapping(portObis);

                        int portClassId = Integer.parseInt(portParts[0]);
                        String portCode = portParts[1];
                        int portAttributeId = Integer.parseInt(portParts[2]);

                        log.info(
                                "Writing Port {} to meter {} " + "(Class: {}, OBIS: {}, Attribute: {})",
                                port,
                                meterSerial,
                                portClassId,
                                portCode,
                                portAttributeId
                        );

                        response = dlmsReaderUtils.writeAttribute(
                                client,
                                meterSerial,
                                portCode,
                                portClassId,
                                portAttributeId,
                                port.getBytes(StandardCharsets.UTF_8),
                                DataType.OCTET_STRING
                        );
                    }
                }

                // Strict validation check to avoid false-positives
                if (response != null && (response.isSuccess() || "SUCCESS".equalsIgnoreCase(response.getStatus().toString()))) {
                    result.put("status", "success");
                    result.put("dlmsStatus", "SUCCESS");
                    result.put("message", "✅ Both IP and Port configurations successfully written and recognized.");
                    log.info("✅ Destination parameters written successfully to meter {}", meterSerial);
                } else {
                    result.put("status", "failed");
                    result.put("dlmsStatus", response != null ? response.getStatus() : "UNKNOWN_ERROR");
                    result.put("message", response != null ? response.getMessage() : "Meter rejected network parameter updates.");
                    log.error("❌ Failed to write configuration to meter {}: {}", meterSerial, result.get("message"));
                }

            } catch (Exception e) {
                // Guarantee that communication failure states force a failure response on the frontend
                result.put("status", "failed");
                result.put("dlmsStatus", "COMMUNICATION_ERROR");
                result.put("message", "❌ Communication error during network configuration sequence: " + e.getMessage());
                log.error("💥 Exception while configuring network settings for meter {}: ", meterSerial, e);
            }

            return result;
        });
    }

    private String[] parseObisMapping(ObisCodeEntity obis) {
        String code = obis.getCode();
        String[] parts = code.split(";");

        if (parts.length < 3) {
            throw new IllegalStateException( "Invalid OBIS mapping: " + code + ". Expected format: " + "(classId;obisCode;attributeIndex)");
        }
        return parts;
    }

    private boolean isSuccessful(DlmsResponse response) {
        return response != null
                && (
                response.isSuccess() || "SUCCESS".equalsIgnoreCase(String.valueOf(response.getStatus()))
        );
    }
}