package com.memmcol.hes.controller;

import com.memmcol.hes.service.ObisConfirmationService;
import com.memmcol.hes.service.ObisMappingImportService;
import com.memmcol.hes.service.ObisScalerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/obis")
@RequiredArgsConstructor
public class ObisMappingController {

    private final ObisMappingImportService importService;
    private final ObisScalerService scalerService;
    private final ObisConfirmationService confirmationService;

    @PostMapping("/import-from-file/{model}")
    public ResponseEntity<?> importObisMappings(@PathVariable String model,
            @RequestParam(defaultValue = "false") boolean hexNotation) {
        try {
            Map<String, Object> response = importService.importFromCsvFile(model, hexNotation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Import failed: " + e.getMessage());
        }
    }

    @PostMapping("/update-scaler-unit/{meterModel}/{meterSerial}")
    public ResponseEntity<?> getObisMappingForMeter(
            @PathVariable String meterSerial,
            @PathVariable String meterModel) {
        try {
            Map<String, Object> mapping = scalerService.updateScalerUnitForMeter(meterSerial, meterModel);

            if (mapping.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No OBIS mapping found for meter serial: " + meterSerial);
            }
            return ResponseEntity.ok(mapping);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving OBIS mapping: " + ex.getMessage());
        }
    }

    @PostMapping("/{meterId}/confirm-obis")
    public ResponseEntity<?> confirmObisCodes(@PathVariable String meterId) {
        try {
            Map<String, Object> response = confirmationService.confirmObisCodes(meterId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error confirming OBIS codes: " + ex.getMessage()));
        }
    }
}
