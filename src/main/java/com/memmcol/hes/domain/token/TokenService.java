package com.memmcol.hes.domain.token;

import com.memmcol.hes.application.port.out.MeterLockPort;
import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.dto.MeterDTO;
import com.memmcol.hes.exception.AssociationLostException;
import com.memmcol.hes.infrastructure.dlms.DlmsReaderUtils;
import com.memmcol.hes.model.DlmsResponse;
import com.memmcol.hes.model.DlmsResponseStatus;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.model.TokenWriteResult;
import com.memmcol.hes.nettyUtils.SessionManagerMultiVendor;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.ObisCodeRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXDLMSExceptionResponse;
import gurux.dlms.GXReplyData;
import gurux.dlms.objects.GXDLMSData;
import gurux.dlms.objects.GXDLMSRegister;
import gurux.dlms.internal.GXCommon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final SessionManagerMultiVendor sessionManager;
    private final DlmsReaderUtils dlmsReaderUtils;
    private final MeterLockPort meterLockPort;
    private final TxRxService txRxService;
    private final MeterRepository meterRepository;
    private final ObisCodeRepository obisCodeRepository;
    private static  final String TOKEN_ACTION = "Send Token";

//    public static final String TOKEN_OBIS = "1.0.129.129.2.255";
//    public static final int TOKEN_CLASS_ID = 1;
//    public static final int TOKEN_ATTRIBUTE = 2;


    public Map<String, Object> writeToken(String meterSerial, String tokenHex) throws Exception {
        return meterLockPort.withExclusive(meterSerial, () -> {
            GXDLMSClient client = sessionManager.getOrCreateClient(meterSerial);
            if (client == null) {
                throw new IllegalStateException("No DLMS session found for meter: " + meterSerial);
            }

            MeterDTO meter = meterRepository.findMeterDetailsByMeterNumber(meterSerial)
                    .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterSerial));

            String model = meter.getMeterModel();

            List<ObisCodeEntity> obisEntity = obisCodeRepository.findActiveByModelAndAction(model, TOKEN_ACTION);
            if (obisEntity.isEmpty()) {
                throw new IllegalStateException(
                        "No OBIS mapping found for model=" + model + " action=" + TOKEN_ACTION
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

            client.setUseLogicalNameReferencing(true);

            // 2. Define the Token Object
            GXDLMSData tokenObject = new GXDLMSData(obisCode);

            log.info("Step 1: Writing token to meter {}", meterSerial);
            log.debug("Token hex bytes: {}", GXCommon.hexToBytes(tokenHex));

            byte[] tokenBytes = GXCommon.hexToBytes(tokenHex);

            // 4. SET THE VALUE INSIDE THE OBJECT FIRST
            tokenObject.setValue(tokenBytes);

            // 5. Generate the Write Request with 2 parameters
            // Attribute 2 is the 'Value' attribute index
            byte[][] writeRequest = client.write(tokenObject, attributeId);

            DlmsResponse response = dlmsReaderUtils.executeMethod(client,meterSerial,writeRequest);

            if (response.getStatus() != DlmsResponseStatus.SUCCESS) {

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("meterSerial", meterSerial);
                result.put("token", tokenHex);
                result.put("status", "failed");
                result.put("dlmsStatus", response.getStatus());
                result.put("message", response.getMessage());

                return result;
            }

            if (response.getRawResponse() == null || response.getRawResponse().isBlank()) {
                throw new IllegalStateException("Meter returned an empty DLMS response.");
            }

            byte[] rawBytes = GXCommon.hexToBytes(response.getRawResponse().replace(" ", ""));

            TokenWriteResult tokenResult = dlmsReaderUtils.parseTokenResponse(rawBytes);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("meterSerial", meterSerial);
            result.put("token", tokenHex);
            result.put("status", tokenResult.isSuccess() ? "success" : "failed");
            result.put("dlmsStatus", response.getStatus());
            result.put("message", response.getMessage());
            result.put("tokenStatus", tokenResult.getTokenStatus());
            result.put("tokenResultCode", tokenResult.getTokenStatus().getCode());
            result.put("meterCreditBalance", tokenResult.getMeterCredit());
            result.put("logoutToken", tokenResult.getLogoutToken());
//            result.put("rawResponse", tokenResult.getRawHex());

            if (tokenResult.isSuccess()) {
                log.info("✅ Token written successfully to meter {}. Status={}, Credit={}", 
                        meterSerial, tokenResult.getTokenStatusLabel(), tokenResult.getMeterCredit());
            } else {
                log.error("❌ Token write failed for meter {}: status={} (code={}), detail={}", 
                        meterSerial, tokenResult.getTokenStatusLabel(), 
                        tokenResult.getTokenStatus().getCode(), tokenResult.getErrorDetail());
            }

            return result;
        });
    }
}