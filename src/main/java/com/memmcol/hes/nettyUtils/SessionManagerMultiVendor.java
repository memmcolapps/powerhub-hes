package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.model.Meter;
import com.memmcol.hes.model.MeterIntegration;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.service.MeterConnections;
import com.memmcol.hes.service.MeterSession;
import gurux.dlms.GXByteBuffer;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXICipher;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.enums.Security;
import gurux.dlms.internal.GXCommon;
import gurux.dlms.objects.enums.SecuritySuite;
import gurux.dlms.secure.GXCiphering;
import gurux.dlms.secure.GXDLMSSecureClient;
import io.netty.channel.Channel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.memmcol.hes.nettyUtils.RequestResponseService.TRACKER;
import static com.memmcol.hes.nettyUtils.RequestResponseService.inflightRequests;

@Service
@Slf4j
public class SessionManagerMultiVendor {
    private final Map<String, MeterSession> sessionMap = new ConcurrentHashMap<>();
    private final Duration SESSION_TIMEOUT = Duration.ofMinutes(3);
    private final TxRxService txRxService;
    private final MeterRepository meterRepository;

    public SessionManagerMultiVendor(TxRxService txRxService, MeterRepository meterRepository) {
        this.txRxService = txRxService;
        this.meterRepository = meterRepository;
    }

    @PostConstruct
    public void init() {
        cleanInflightRequests();
    }

    /**
     * Creates a DLMS client by querying MeterIntegration from DB.
     */
    private GXDLMSClient createDlmsClient(String serial) {
        MeterIntegration integration = meterRepository.findByMeterNumber(serial)
                .map(Meter::getMeterIntegration)
                .orElse(null);

        int clientId = parseClientId(integration != null ? integration.getClientId() : null);
        int serverId = 1;
        Authentication auth = parseAuthentication(integration != null ? integration.getAuthenticationType() : null);
        String password = integration != null && integration.getPassword() != null ? integration.getPassword() : "12345678";
        InterfaceType interfaceType = parseInterfaceType(integration != null ? integration.getProtocol() : null);

        if (auth == Authentication.HIGH_GMAC || auth == Authentication.HIGH) {
            GXDLMSSecureClient secureClient = new GXDLMSSecureClient(
                    true,
                    clientId,
                    serverId,
                    auth,
                    password,
                    interfaceType
            );

            configureCiphering(secureClient, integration);
            return secureClient;
        }

        return new GXDLMSClient(
                true,
                clientId,
                serverId,
                auth,
                password,
                interfaceType
        );
    }

    private void configureCiphering(GXDLMSSecureClient client, MeterIntegration integration) {
        GXCiphering ciphering = client.getCiphering();
        ciphering.setSecurity(Security.AUTHENTICATION);

        if (integration != null) {
            if (integration.getSerial() != null && !integration.getSerial().isBlank()) {
                ciphering.setSystemTitle(GXCommon.hexToBytes(integration.getSerial()));
            }
            if (integration.getEncryptionKey() != null && !integration.getEncryptionKey().isBlank()) {
                ciphering.setBlockCipherKey(GXCommon.hexToBytes(integration.getEncryptionKey()));
            }
            if (integration.getAuthMechanism() != null && !integration.getAuthMechanism().isBlank()) {
                ciphering.setAuthenticationKey(GXCommon.hexToBytes(integration.getAuthMechanism()));
            }
            if (integration.getGlobalBroadcastEncryptionKey() != null && !integration.getGlobalBroadcastEncryptionKey().isBlank()) {
                ciphering.setBroadcastBlockCipherKey(GXCommon.hexToBytes(integration.getGlobalBroadcastEncryptionKey()));
            }
            if (integration.getMasterKey() != null && !integration.getMasterKey().isBlank()) {
                ciphering.setDedicatedKey(GXCommon.hexToBytes(integration.getMasterKey()));
            }
        }
        ciphering.setSecuritySuite(SecuritySuite.SUITE_0);
        ciphering.setInvocationCounter(1);
    }

    private int parseClientId(String raw) {
        if (raw != null) {
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid client_id in DB: {}", raw);
            }
        }
        return 1;
    }

    private Authentication parseAuthentication(String raw) {
        if (raw != null) {
            try {
                return Authentication.valueOf(raw.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                if ("LOW_SECURITY".equalsIgnoreCase(raw.trim())) {
                    return Authentication.LOW;
                } else if ("HIGH_SECURITY".equalsIgnoreCase(raw.trim())) {
                    return Authentication.HIGH;
                } else if ("HIGH_GMAC".equalsIgnoreCase(raw.trim())) {
                    return Authentication.HIGH_GMAC;
                } else if ("NONE".equalsIgnoreCase(raw.trim())) {
                    return Authentication.NONE;
                }
                log.warn("Unrecognized authentication_type in DB: {}", raw);
            }
        }
        return Authentication.LOW;
    }

    private InterfaceType parseInterfaceType(String raw) {
        if (raw != null) {
            try {
                return InterfaceType.valueOf(raw.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                if ("TCP".equalsIgnoreCase(raw.trim()) || "IP".equalsIgnoreCase(raw.trim())) {
                    return InterfaceType.WRAPPER;
                }
                log.warn("Unrecognized protocol/interface_type in DB: {}", raw);
            }
        }
        return InterfaceType.WRAPPER;
    }

    /**
     * Adds or reuses a session for a given meter.
     */
    public synchronized void addSession(String serial, Channel channel) throws Exception {
        if (channel == null) {
            log.info("No channel provided for session with meter {}", serial);
        }

        MeterSession existing = sessionMap.get(serial);
        if (existing != null && existing.isAssociated()) {
            log.debug("Session already exists and is associated: {}", serial);
            return;
        }

        GXDLMSClient dlmsClient = createDlmsClient(serial);
        String modelId = meterRepository.findByMeterNumber(serial)
                .map(m -> m.getMeterIntegration().getModel())
                .orElse("UNKNOWN");

        try {
            log.info("🔗 Setting up DLMS Association for {} (Model: {})", serial, modelId);
            byte[][] aarq = dlmsClient.aarqRequest();
            byte[] response = txRxService.sendReceiveWithContext(serial, aarq[0], 20000);

            if (response != null && response.length >= 8) {
                byte[] payload = Arrays.copyOfRange(response, 8, response.length);
                GXByteBuffer replyBuffer = new GXByteBuffer(payload);
                try {
                    dlmsClient.parseAareResponse(replyBuffer);
                } catch (IllegalArgumentException e) {
                    log.warn("⚠️ AARE parse failed for {}: {}", serial, e.getMessage());
                }
            }

            log.info("✅ DLMS Association established for {} (Model: {})", serial, modelId);
            MeterSession meterSession = new MeterSession(serial, channel, dlmsClient, txRxService);
            meterSession.setAssociated(true);
            sessionMap.put(serial, meterSession);

        } catch (Exception e) {
            log.error("❌ DLMS Association failed for {}: {}", serial, e.getMessage());
            throw e;
        }
    }

    public GXDLMSClient getClient(String serial) {
        MeterSession session = sessionMap.get(serial);
        if (session != null && session.isAssociated()) {
            return session.getClient();
        }
        return null;
    }

    public GXDLMSClient getOrCreateClient(String serial) throws Exception {
        GXDLMSClient client = getClient(serial);
        if (client == null) {
            addSession(serial, MeterConnections.getChannel(serial));
            client = getClient(serial);
        }
        return client;
    }

    public void removeSession(String serial) {
        sessionMap.remove(serial);
    }

    public boolean isAssociationLost(byte[] response) {
        if (response == null || response.length < 3) return false;
        int len = response.length;
        return response[len - 3] == (byte) 0xD8
                && response[len - 2] == 0x01
                && response[len - 1] == 0x01;
    }

    public void cleanInflightRequests() {
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            inflightRequests.entrySet().removeIf(entry -> {
                DlmsRequestContext ctx = entry.getValue();
                if (now > ctx.getExpiryTime()) {
                    log.warn("🧹 Cleaning expired request: CID={}, Meters={}",
                            entry.getKey(), ctx.getMeterId());
                    TRACKER.remove(entry.getKey());
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

//    @Scheduled(fixedDelay = 30000)
    public void cleanupExpiredEntries() {
        log.debug("🔍 Cleaning expired sessions and inflight TXs...");
//
//        int before = sessionMap.size();
//        sessionMap.entrySet().removeIf(entry -> {
//            MeterSession session = entry.getValue();
//            if (session.isExpired(SESSION_TIMEOUT)) {
//                try {
//                    session.sendDisconnectRequest(session);
//                    log.warn("⏳ Disconnected expired meter {}", entry.getKey());
//                } catch (Exception e) {
//                    log.error("❌ Failed to disconnect {}: {}", entry.getKey(), e.getMessage());
//                }
//                return true;
//            }
//            return false;
//        });
//        log.debug("🧹 Removed {} expired sessions", before - sessionMap.size());
    }
}
