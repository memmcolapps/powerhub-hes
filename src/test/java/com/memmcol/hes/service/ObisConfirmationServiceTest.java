package com.memmcol.hes.service;

import com.memmcol.hes.model.Meter;
import com.memmcol.hes.model.MeterIntegration;
import com.memmcol.hes.model.ModelProfileMetadata;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.repository.MeterRepository;
import com.memmcol.hes.repository.ObisCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObisConfirmationServiceTest {

    @Mock
    private MeterRepository meterRepository;

    @Mock
    private ObisCodeRepository obisCodeRepository;

    @Mock
    private ProfileMetadataService profileMetadataService;

    @InjectMocks
    private ObisConfirmationService obisConfirmationService;

    private Meter testMeter;
    private MeterIntegration testIntegration;
    private ObisCodeEntity testObisCode;
    private UUID meterId;
    private UUID obisId;

    @BeforeEach
    void setUp() {
        meterId = UUID.randomUUID();
        obisId = UUID.randomUUID();

        testIntegration = new MeterIntegration();
        testIntegration.setModel("MMX-313-CT");

        testMeter = new Meter();
        testMeter.setId(meterId);
        testMeter.setMeterNumber("202006001314");
        testMeter.setMeterIntegration(testIntegration);

        testObisCode = new ObisCodeEntity();
        testObisCode.setId(obisId);
        testObisCode.setCode("1.0.99.1.0.255");
        testObisCode.setAction("PROFILE_GENERIC");
        testObisCode.setObisType("PROFILE");
        testObisCode.setStatus("ACTIVE");
        testObisCode.setConfirmation("PENDING");
    }

    @Test
    @DisplayName("confirmObisCodes should update status to CONFIRMED and SUCCESS when read returns metadata")
    void confirmObisCodes_Success() {
        when(meterRepository.findById(meterId)).thenReturn(Optional.of(testMeter));
        when(obisCodeRepository.findActiveByModel("MMX-313-CT")).thenReturn(List.of(testObisCode));
        when(obisCodeRepository.findById(obisId)).thenReturn(Optional.of(testObisCode));

        ModelProfileMetadata metadata = ModelProfileMetadata.builder()
                .meterModel("MMX-313-CT")
                .profileObis("1.0.99.1.0.255")
                .captureObis("0.0.1.0.0.255")
                .build();

        when(profileMetadataService.loadFromMeterAndPersist("202006001314", "MMX-313-CT", "1.0.99.1.0.255"))
                .thenReturn(List.of(metadata));

        Map<String, Object> result = obisConfirmationService.confirmObisCodes(meterId.toString(), null);

        assertNotNull(result);
        assertEquals("CONFIRMED", result.get("overallStatus"));
        assertEquals("CONFIRMED", testObisCode.getConfirmation());
        assertEquals("SUCCESS", testObisCode.getResponse());
        verify(obisCodeRepository, times(1)).save(testObisCode);
    }

    @Test
    @DisplayName("confirmObisCodes with semicolon-delimited OBIS code should match and update correctly")
    void confirmObisCodes_SemicolonDelimitedObisCode_Success() {
        ObisCodeEntity semicolonEntity = new ObisCodeEntity();
        semicolonEntity.setId(obisId);
        semicolonEntity.setCode("7;1.0.99.1.0.255;2;0");
        semicolonEntity.setAction("PROFILE_GENERIC");
        semicolonEntity.setObisType("PROFILE");
        semicolonEntity.setStatus("ACTIVE");
        semicolonEntity.setConfirmation("PENDING");

        when(meterRepository.findById(meterId)).thenReturn(Optional.of(testMeter));
        when(obisCodeRepository.findActiveByModel("MMX-313-CT")).thenReturn(List.of(semicolonEntity));
        when(obisCodeRepository.findById(obisId)).thenReturn(Optional.of(semicolonEntity));

        ModelProfileMetadata metadata = ModelProfileMetadata.builder()
                .meterModel("MMX-313-CT")
                .profileObis("1.0.99.1.0.255")
                .captureObis("0.0.1.0.0.255")
                .build();

        when(profileMetadataService.loadFromMeterAndPersist("202006001314", "MMX-313-CT", "7;1.0.99.1.0.255;2;0"))
                .thenReturn(List.of(metadata));

        Map<String, Object> result = obisConfirmationService.confirmObisCodes(meterId.toString(), "1.0.99.1.0.255");

        assertNotNull(result);
        assertEquals("CONFIRMED", result.get("overallStatus"));
        assertEquals("CONFIRMED", semicolonEntity.getConfirmation());
        assertEquals("SUCCESS", semicolonEntity.getResponse());
        verify(obisCodeRepository, times(1)).save(semicolonEntity);
    }

    @Test
    @DisplayName("confirmObisCodes with specific target OBIS code should update that specific code")
    void confirmObisCodes_SpecificObisCode_Success() {
        when(meterRepository.findById(meterId)).thenReturn(Optional.of(testMeter));
        when(obisCodeRepository.findActiveByModel("MMX-313-CT")).thenReturn(List.of(testObisCode));
        when(obisCodeRepository.findById(obisId)).thenReturn(Optional.of(testObisCode));

        ModelProfileMetadata metadata = ModelProfileMetadata.builder()
                .meterModel("MMX-313-CT")
                .profileObis("1.0.99.1.0.255")
                .captureObis("0.0.1.0.0.255")
                .build();

        when(profileMetadataService.loadFromMeterAndPersist("202006001314", "MMX-313-CT", "1.0.99.1.0.255"))
                .thenReturn(List.of(metadata));

        Map<String, Object> result = obisConfirmationService.confirmObisCodes(meterId.toString(), "1.0.99.1.0.255");

        assertNotNull(result);
        assertEquals("CONFIRMED", result.get("overallStatus"));
        assertEquals("CONFIRMED", testObisCode.getConfirmation());
        assertEquals("SUCCESS", testObisCode.getResponse());
        verify(obisCodeRepository, times(1)).save(testObisCode);
    }

    @Test
    @DisplayName("confirmObisCodes should update status to FAILED and record error message on exception")
    void confirmObisCodes_Failure_Exception() {
        when(meterRepository.findById(meterId)).thenReturn(Optional.of(testMeter));
        when(obisCodeRepository.findActiveByModel("MMX-313-CT")).thenReturn(List.of(testObisCode));
        when(obisCodeRepository.findById(obisId)).thenReturn(Optional.of(testObisCode));

        when(profileMetadataService.loadFromMeterAndPersist("202006001314", "MMX-313-CT", "1.0.99.1.0.255"))
                .thenThrow(new RuntimeException("Connection timeout with meter"));

        Map<String, Object> result = obisConfirmationService.confirmObisCodes(meterId.toString(), null);

        assertNotNull(result);
        assertEquals("FAILED", result.get("overallStatus"));
        assertEquals("FAILED", testObisCode.getConfirmation());
        assertEquals("Connection timeout with meter", testObisCode.getResponse());
        verify(obisCodeRepository, times(1)).save(testObisCode);
    }

    @Test
    @DisplayName("confirmObisCodes should throw IllegalStateException when no OBIS codes exist in database")
    void confirmObisCodes_NoObisCodesInDatabase() {
        when(meterRepository.findById(meterId)).thenReturn(Optional.empty());
        when(meterRepository.findByMeterNumber(meterId.toString())).thenReturn(Optional.empty());
        when(obisCodeRepository.findAll()).thenReturn(Collections.emptyList());

        assertThrows(IllegalStateException.class, () ->
                obisConfirmationService.confirmObisCodes(meterId.toString(), null));
    }
}
