package com.memmcol.hes.service;

import com.memmcol.hes.model.MeterIntegration;
import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.repository.MeterIntegrationRepository;
import com.memmcol.hes.repository.ObisCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObisMappingImportService {
    private final ObisCodeRepository obisCodeRepository;
    private final MeterIntegrationRepository meterIntegrationRepository;

    private static final String CSV_FILE_PATH = "./obis_mapping.csv";

    public Map<String, Object> importFromCsvFile(String model, boolean hexNotation) throws IOException {
        List<ObisCodeEntity> successfulImports = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;
        File file = new File(CSV_FILE_PATH);
        String line = "";

        if (!file.exists()) {
            log.error("CSV file not found: {}", CSV_FILE_PATH);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "CSV file not found: " + CSV_FILE_PATH);
            result.put("successful_count", successCount);
            result.put("failed_count", failureCount);
            result.put("successful_imports", Collections.emptyList());
            return result;
        }

        Optional<MeterIntegration> integrationOpt = meterIntegrationRepository.findByModelIgnoreCase(model);
        if (integrationOpt.isEmpty()) {
            log.error("Meter integration not found for model: {}", model);
            Map<String, Object> result = new HashMap<>();
            result.put("error", "Meter integration not found for model: " + model);
            result.put("successful_count", successCount);
            result.put("failed_count", failureCount);
            result.put("successful_imports", Collections.emptyList());
            return result;
        }
        MeterIntegration integration = integrationOpt.get();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // skip header
                    continue;
                }

                String[] columns = line.split(",", -1);

                if (columns.length < 9) continue;

                String obisCodeCombined;
                int classId, attributeIndex;

                if (hexNotation) {
                    classId = Integer.parseInt(columns[3].trim());
                    String dotNotation = hexToDotNotation(columns[4].trim());
                    attributeIndex = Integer.parseInt(columns[5].trim());
                    obisCodeCombined = classId + ";" + dotNotation + ";" + attributeIndex + ";0";
                } else {
                    classId = Integer.parseInt(columns[3].trim());
                    attributeIndex = Integer.parseInt(columns[5].trim());
                    obisCodeCombined = classId + ";" + columns[4].trim() + ";" + attributeIndex + ";0";
                }

                ObisCodeEntity obis = new ObisCodeEntity();
                obis.setId(UUID.randomUUID());
                obis.setMeterIntegration(integration);
                obis.setAction(columns[1].isBlank() ? columns[2].trim() : columns[1].trim());
                obis.setCode(obisCodeCombined);
                obis.setDescription(columns[2].trim());
                obis.setStatus("ACTIVE");
                obis.setObisType("REAL_TIME");
                obis.setScaler(columns[7].isBlank() ? null : columns[7].trim());
                obis.setUnit(columns.length > 8 ? columns[8].trim() : null);
                obis.setMultiplyBy("NONE");

                obisCodeRepository.save(obis);
                successfulImports.add(obis);
                successCount++;
            }
        } catch (IOException ex) {
            log.error("Error reading CSV on line {} : {}", line, ex.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("Error", "Error reading CSV on line " + line + " : " + ex.getMessage());
            result.put("successful_count", successCount);
            result.put("failed_count", failureCount);
            result.put("successful_imports", successfulImports);
            return result;
        } catch (Exception e) {
            log.error("Error parsing CSV on line {} : {}", line, e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("Error", "Error parsing CSV on line " + line + " : " + e.getMessage());
            result.put("successful_count", successCount);
            result.put("failed_count", failureCount);
            result.put("successful_imports", successfulImports);
            return result;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successful", "CSV Extracted successfully");
        result.put("successful_count", successCount);
        result.put("failed_count", failureCount);
        result.put("successful_imports", successfulImports);

        return result;
    }

    private String hexToDotNotation(String hex) {
        if (hex.length() != 12) throw new IllegalArgumentException("Hex OBIS code must be 12 characters");

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 12; i += 2) {
            String byteStr = hex.substring(i, i + 2);
            int part = Integer.parseInt(byteStr, 16);
            parts.add(String.valueOf(part));
        }
        return String.join(".", parts);
    }
}
