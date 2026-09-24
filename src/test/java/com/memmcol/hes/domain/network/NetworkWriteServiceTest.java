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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NetworkWriteServiceTest {

    @Mock
    private SessionManagerMultiVendor sessionManager;

    @Mock
    private DlmsReaderUtils dlmsReaderUtils;

    @Mock
    private MeterLockPort meterLockPort;

    @Mock
    private MeterRepository meterRepository;

    @Mock
    private ObisCodeRepository obisCodeRepository;

    @Mock
    private GXDLMSClient dlmsClient;

    @InjectMocks
    private NetworkWriteService networkWriteService;

    private final String serial = "202006002221";

    @BeforeEach
    void setup() throws Exception {
        lenient().when(meterLockPort.withExclusive(eq(serial), any(Callable.class))).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(1);
            return callable.call();
        });
    }

    @Test
    void testWriteApn_MD() throws Exception {
        String apn = "Internet.ng.airtel.com";
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("MD").meterModel("MD_MODEL").build()
        ));
        ObisCodeEntity obisEntity = new ObisCodeEntity();
        obisEntity.setCode("45;0.0.25.4.0.255;2;0");
        when(obisCodeRepository.findActiveByModelAndAction("MD_MODEL", "APN Operation"))
                .thenReturn(List.of(obisEntity));

        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        Map<String, Object> result = networkWriteService.writeApn(serial, apn);

        assertEquals("success", result.get("status"));
        assertEquals(apn, result.get("apn"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.0.25.4.0.255"), eq(45), eq(2), any(byte[].class), eq(DataType.OCTET_STRING));
    }

    @Test
    void testWriteApn_NonMD() throws Exception {
        String apn = "Internet.ng.airtel.com";
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("Three-Phase").meterModel("NON_MD_MODEL").build()
        ));
        ObisCodeEntity obisEntity = new ObisCodeEntity();
        obisEntity.setCode("45;0.11.25.4.0.255;2;0");
        when(obisCodeRepository.findActiveByModelAndAction("NON_MD_MODEL", "APN Operation"))
                .thenReturn(List.of(obisEntity));

        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        Map<String, Object> result = networkWriteService.writeApn(serial, apn);

        assertEquals("success", result.get("status"));
        assertEquals(apn, result.get("apn"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.11.25.4.0.255"), eq(45), eq(2), any(byte[].class), eq(DataType.OCTET_STRING));
    }

    @Test
    void testWriteIpPort_MD() throws Exception {
        List<String> ipPorts = List.of("41.216.166.165:29064");
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("MD").meterModel("MD_MODEL").build()
        ));
        ObisCodeEntity obisEntity = new ObisCodeEntity();
        obisEntity.setCode("29;0.0.2.1.0.255;6;0");
        when(obisCodeRepository.findActiveByModelAndAction("MD_MODEL", "IP/Port Operation"))
                .thenReturn(List.of(obisEntity));

        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        Map<String, Object> result = networkWriteService.writeIpPort(serial, ipPorts);

        assertEquals("success", result.get("status"));
        assertEquals(ipPorts, result.get("ipPorts"));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.0.2.1.0.255"), eq(29), eq(6), anyList(), eq(DataType.ARRAY));
    }

    @Test
    void testWriteIpPort_NonMD() throws Exception {
        String ip = "41.216.166.165";
        int port = 29064;
        List<String> ipPorts = List.of(ip + ":" + port);
        when(sessionManager.getOrCreateClient(serial)).thenReturn(dlmsClient);
        when(meterRepository.findMeterDetailsByMeterNumber(serial)).thenReturn(java.util.Optional.of(
                MeterDTO.builder().meterClass("Single-Phase").meterModel("NON_MD_MODEL").build()
        ));
        ObisCodeEntity ipObis = new ObisCodeEntity();
        ipObis.setCode("45;0.11.25.4.0.255;5;0");
        ObisCodeEntity portObis = new ObisCodeEntity();
        portObis.setCode("41;0.11.25.0.0.255;2;0");
        when(obisCodeRepository.findActiveByModelAndAction("NON_MD_MODEL", "IP/Port Operation"))
                .thenReturn(List.of(ipObis, portObis));

        when(dlmsReaderUtils.writeAttribute(any(), anyString(), anyString(), anyInt(), anyInt(), any(), any())).thenReturn(
                DlmsResponse.builder().meterSerial(serial).status(com.memmcol.hes.model.DlmsResponseStatus.SUCCESS).build()
        );

        Map<String, Object> result = networkWriteService.writeIpPort(serial, ipPorts);

        assertEquals("success", result.get("status"));
        assertEquals(ipPorts, result.get("ipPorts"));

        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.11.25.4.0.255"), eq(45), eq(5), any(byte[].class), eq(DataType.OCTET_STRING));
        verify(dlmsReaderUtils).writeAttribute(eq(dlmsClient), eq(serial), eq("0.11.25.0.0.255"), eq(41), eq(2), any(byte[].class), eq(DataType.OCTET_STRING));
    }
}
