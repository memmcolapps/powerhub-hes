package com.memmcol.hes.nettyUtils;

import com.memmcol.hes.application.port.out.TxRxService;
import com.memmcol.hes.model.Meter;
import com.memmcol.hes.model.MeterIntegration;
import com.memmcol.hes.repository.MeterRepository;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.enums.Authentication;
import gurux.dlms.enums.InterfaceType;
import gurux.dlms.secure.GXDLMSSecureClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionManagerMultiVendorTest {

    @Mock
    private TxRxService txRxService;

    @Mock
    private MeterRepository meterRepository;

    private SessionManagerMultiVendor sessionManager;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager = new SessionManagerMultiVendor(txRxService, meterRepository);
        lenient().when(txRxService.sendReceiveWithContext(anyString(), any(), anyLong()))
                .thenReturn(new byte[10]);
    }

    @Test
    void testGetOrCreateClient_LoadsFromRepository_StandardClient() throws Exception {
        String serial = "202006001234";

        MeterIntegration integration = MeterIntegration.builder()
                .clientId("1")
                .authenticationType("LOW_SECURITY")
                .password("87654321")
                .protocol("TCP")
                .model("MOMAS")
                .build();

        Meter meter = Meter.builder()
                .meterNumber(serial)
                .meterIntegration(integration)
                .build();

        when(meterRepository.findByMeterNumber(serial)).thenReturn(Optional.of(meter));

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);

        assertNotNull(client);
        assertFalse(client instanceof GXDLMSSecureClient);
        assertEquals(1, client.getClientAddress());
        assertEquals(Authentication.LOW, client.getAuthentication());
        assertEquals(InterfaceType.WRAPPER, client.getInterfaceType());
        verify(meterRepository, atLeastOnce()).findByMeterNumber(serial);
    }

    @Test
    void testGetOrCreateClient_LoadsFromRepository_SecureClient() throws Exception {
        String serial = "62124009999";

        MeterIntegration integration = MeterIntegration.builder()
                .clientId("2")
                .authenticationType("HIGH_GMAC")
                .password("00000000")
                .protocol("IP")
                .model("LONGDIAN")
                .serial("4C44430000000001")
                .encryptionKey("00000000000000000000000000000000")
                .authMechanism("30303030303030303030303030303030")
                .globalBroadcastEncryptionKey("30303030303030303030303030303030")
                .masterKey("30303030303030303030303030303030")
                .build();

        Meter meter = Meter.builder()
                .meterNumber(serial)
                .meterIntegration(integration)
                .build();

        when(meterRepository.findByMeterNumber(serial)).thenReturn(Optional.of(meter));

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);

        assertNotNull(client);
        assertTrue(client instanceof GXDLMSSecureClient);
        assertEquals(2, client.getClientAddress());
        assertEquals(Authentication.HIGH_GMAC, client.getAuthentication());
        assertEquals(InterfaceType.WRAPPER, client.getInterfaceType());
    }

    @Test
    void testGetOrCreateClient_FallbackDefaultsWhenMeterNotFound() throws Exception {
        String serial = "UNKNOWN123";

        when(meterRepository.findByMeterNumber(serial)).thenReturn(Optional.empty());

        GXDLMSClient client = sessionManager.getOrCreateClient(serial);

        assertNotNull(client);
        assertEquals(1, client.getClientAddress());
        assertEquals(Authentication.LOW, client.getAuthentication());
        assertEquals(InterfaceType.WRAPPER, client.getInterfaceType());
    }
}
