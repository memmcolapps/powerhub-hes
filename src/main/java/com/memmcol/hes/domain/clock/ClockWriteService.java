package com.memmcol.hes.domain.clock;

import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.ObisCodeRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDateTime;
import gurux.dlms.objects.GXDLMSClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClockWriteService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterRepository meterRepository;
    private final ObisCodeRepository obisCodeRepository;
    private static final String CLOCK_ACTION = "Send Token";

    /**
     * Sets the meter clock (date and time) using the shared DLMS session and DlmsReaderUtils.writeAttribute.
     *
     * @param serial   meter serial number
     * @param dateTime local date-time to set on the meter
     */
    public Map<String, Object> setClock(String serial, LocalDateTime dateTime) throws Exception {
        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) {
            throw new IllegalStateException("No DLMS session found for meter: " + serial);
        }

        MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(serial)
                .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + serial));

        String model = meter.getMeterModel();

        List<ObisCodeEntity> obisEntity = obisCodeRepository.findActiveByModelAndAction(model, CLOCK_ACTION);
        if (obisEntity.isEmpty()) {
            throw new IllegalStateException(
                    "No OBIS mapping found for model=" + model + " action=" + CLOCK_ACTION
            );
        }

        ObisCodeEntity obis = obisEntity.get(0);

        String[] parts = obis.getCode().split(";");
        if (parts.length < 3) {
            throw new IllegalStateException("OBIS code '" + obis.getCode() + "' does not match expected format " + "(classId;obisCode;attributeIndex;dataIndex)");
        }

        int classId = Integer.parseInt(parts[0]);
        String obisCode = parts[1];
        int attributeId = Integer.parseInt(parts[2]);

        GXDLMSClock clock = new GXDLMSClock(obisCode);

        // Gurux GXDateTime handles the complex DLMS structure (12-byte OCTET STRING)
        GXDateTime gxDateTime = new GXDateTime(Date.from(
                dateTime.atZone(ZoneId.systemDefault()).toInstant()
        ));

        DlmsResponse response = dlmsReaderUtils.writeAttribute(client, serial, clock, attributeId, gxDateTime);

        Map<String, Object> result = new HashMap<>();
        result.put("serial", serial);
        result.put("timestamp", LocalDateTime.now());

        String formatted = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (response.isSuccess()) {
            String message = "🕒 Meter Clock for " + serial + " set to: " + formatted;
            log.info(message);
            result.put("status", "success");
            result.put("message", message);
        } else {
            String message = "❌ Failed to set Meter Clock for " + serial + ": " + response.getMessage() + " (" + response.getStatus() + ")";
            log.error(message);
            result.put("status", "failed");
            result.put("message", message);
            result.put("dlmsStatus", response.getStatus());
        }
        return result;
    }

    public String setClockV1(String serial, LocalDateTime dateTime) throws Exception {

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);
        if (client == null) {
            throw new IllegalStateException("No DLMS session found for meter: " + serial);
        }

        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");

        GXDateTime gxDateTime = new GXDateTime(String.valueOf(dateTime));
        gxDateTime.setDeviation(0); // Explicitly control timezone (adjust if needed)

        log.info("🕒 Initiating clock write → meter={}, targetTime={}", serial, dateTime);

        DlmsResponse response = dlmsReaderUtils.writeAttribute(client, serial, clock, 2, gxDateTime);

        if (!response.isSuccess()) {
            String message = "❌ Failed to set Meter Clock (V1) for " + serial + ": " + response.getMessage() + " (" + response.getStatus() + ")";
            log.error(message);
            return message;
        }

        // 🔁 VERIFY (non-negotiable in DLMS writes)
        GXDateTime actual = readClock(client, serial);

        if (!isCloseEnough(actual, gxDateTime)) {
            throw new IllegalStateException(String.format(
                    "Clock verification failed → meter=%s, expected=%s, actual=%s",
                    serial, gxDateTime, actual
            ));
        }

        String formatted = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        log.info("✅ Clock write verified → meter={}, time={}", serial, formatted);

        return "🕒 Meter Clock for " + serial + " set to: " + formatted;
    }

    private boolean isCloseEnough(GXDateTime actual, GXDateTime expected) {

        LocalDateTime a = toLocalDateTime(actual);
        LocalDateTime e = toLocalDateTime(expected);

        long diff = Math.abs(Duration.between(a, e).toSeconds());

        return diff <= 5; // tolerance window (configurable)
    }

    public GXDateTime readClock(GXDLMSClient client, String serial) throws Exception {

        GXDLMSClock clock = new GXDLMSClock("0.0.1.0.0.255");

        Object value = dlmsReaderUtils.readAttribute(client, serial, clock, 2);

        if (!(value instanceof GXDateTime)) {
            throw new IllegalStateException("Invalid clock response → meter=" + serial);
        }

        return (GXDateTime) value;
    }

    private LocalDateTime toLocalDateTime(GXDateTime gxDateTime) {

        Date date = (Date) gxDateTime.getValue();

        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}