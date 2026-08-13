package com.memmcol.hes.domain.profile;

import com.memmcol.hes.model.Meter;
import com.memmcol.hes.model.MeterIntegration;
import com.memmcol.hes.model.Organisation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SaaSMeterIntegrationTest {

    @Test
    void testMeterModelAndRelationshipBuilders() {
        Organisation org = Organisation.builder()
                .businessName("Test Organisation")
                .build();
        org.setId(UUID.randomUUID());

        MeterIntegration integration = MeterIntegration.builder()
                .manufacturer("MEMMCOL")
                .model("MMX-313-CT")
                .meterClass("CT")
                .category("SMART")
                .protocol("TCP")
                .authenticationType("LOW_SECURITY")
                .password("12345678")
                .serial("123456789")
                .status("ACTIVE")
                .build();
        integration.setId(UUID.randomUUID());

        Meter meter = Meter.builder()
                .meterNumber("123456789")
                .simNumber("987654321")
                .organisation(org)
                .meterIntegration(integration)
                .oldSgc("111111")
                .newSgc("222222")
                .oldKrn(1)
                .newKrn(2)
                .oldTariffIndex(1)
                .newTariffIndex(2)
                .status("ACTIVE")
                .build();
        meter.setId(UUID.randomUUID());

        // Verify model fields and types match requirements
        assertNotNull(meter.getId());
        assertEquals("123456789", meter.getMeterNumber());
        assertEquals("987654321", meter.getSimNumber());
        assertEquals("ACTIVE", meter.getStatus());
        assertEquals("111111", meter.getOldSgc());
        assertEquals("222222", meter.getNewSgc());
        assertEquals(1, meter.getOldKrn());
        assertEquals(2, meter.getNewKrn());
        assertEquals(1, meter.getOldTariffIndex());
        assertEquals(2, meter.getNewTariffIndex());

        // Verify relationships
        assertNotNull(meter.getOrganisation());
        assertEquals("Test Organisation", meter.getOrganisation().getBusinessName());
        assertNotNull(meter.getMeterIntegration());
        assertEquals("MEMMCOL", meter.getMeterIntegration().getManufacturer());
        assertEquals("MMX-313-CT", meter.getMeterIntegration().getModel());
        assertEquals("CT", meter.getMeterIntegration().getMeterClass());
        assertEquals("SMART", meter.getMeterIntegration().getCategory());
        assertEquals("TCP", meter.getMeterIntegration().getProtocol());
        assertEquals("LOW_SECURITY", meter.getMeterIntegration().getAuthenticationType());
        assertEquals("12345678", meter.getMeterIntegration().getPassword());
    }
}
