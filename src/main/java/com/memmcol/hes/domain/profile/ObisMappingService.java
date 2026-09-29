package com.memmcol.hes.domain.profile;

import com.memmcol.hes.model.ObisCodeEntity;
import com.memmcol.hes.repository.ObisCodeRepository;
import com.memmcol.hes.service.ObisColumnDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObisMappingService {

    private final ObisCodeRepository obisCodeRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = "modelScalerMap", key = "#model")
    public Map<String, MultiplierDTO> getScalerAndPurposeMap(String model) {
        List<ObisCodeEntity> obisCodes = obisCodeRepository.findActiveByModel(model);
        Map<String, MultiplierDTO> map = new HashMap<>();
        for (ObisCodeEntity o : obisCodes) {
            Double scaler = parseScaler(o.getScaler());
            if (scaler != null) {
                MultiplierDTO dto = new MultiplierDTO(scaler, o.getMultiplyBy(), o.getDescription());
                map.put(o.getCode(), dto);
                String dotNotation = extractObisCode(o.getCode());
                if (dotNotation != null) {
                    map.put(dotNotation, dto);
                    map.put(dotNotation.replace(".", ""), dto);
                } else {
                    map.put(o.getCode().replace(".", ""), dto);
                }
            }
        }
        return map;
    }

    @Cacheable(value = "modelScalerMap", key = "#model")
    public Map<String, Double> getScalersForModel(String model) {
        List<ObisCodeEntity> obisCodes = obisCodeRepository.findActiveByModel(model);
        Map<String, Double> map = new HashMap<>();
        for (ObisCodeEntity o : obisCodes) {
            Double scaler = parseScaler(o.getScaler());
            if (scaler != null) {
                map.put(o.getCode(), scaler);
                String dotNotation = extractObisCode(o.getCode());
                if (dotNotation != null) {
                    map.put(dotNotation, scaler);
                    map.put(dotNotation.replace(".", ""), scaler);
                } else {
                    map.put(o.getCode().replace(".", ""), scaler);
                }
            }
        }
        return map;
    }

    @Cacheable(value = "modelDescriptionMap", key = "#model")
    public Map<String, String> getDescriptionForModel(String model) {
        List<ObisCodeEntity> obisCodes = obisCodeRepository.findActiveByModel(model);
        Map<String, String> map = new HashMap<>();
        for (ObisCodeEntity o : obisCodes) {
            if (o.getDescription() != null) {
                map.put(o.getCode(), o.getDescription());
                String dotNotation = extractObisCode(o.getCode());
                if (dotNotation != null) {
                    map.put(dotNotation, o.getDescription());
                    map.put(dotNotation.replace(".", ""), o.getDescription());
                } else {
                    map.put(o.getCode().replace(".", ""), o.getDescription());
                }
            }
        }
        return map;
    }

    @Cacheable(cacheNames = "obisMappings", key = "T(java.lang.String).format('%s|%s', #model, #purpose)")
    public List<ObisCodeEntity> getMappingsByModelAndPurpose(String model, String purpose) {
        List<ObisCodeEntity> obisCodes = obisCodeRepository.findActiveByModel(model);
        return obisCodes.stream()
                .filter(o -> purpose != null && purpose.equalsIgnoreCase(o.getMultiplyBy()))
                .collect(Collectors.toList());
    }

    @PostConstruct
    public void preloadObisMappings() {
        List<ObisCodeEntity> allObisCodes = obisCodeRepository.findAllWithMeterIntegration();

        Map<String, List<ObisCodeEntity>> mappingsByModel = allObisCodes.stream()
                .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus())
                        && o.getMeterIntegration() != null
                        && o.getMeterIntegration().getModel() != null)
                .collect(Collectors.groupingBy(o -> o.getMeterIntegration().getModel()));

        mappingsByModel.forEach((model, mappings) -> {
            if (cacheManager.getCache("obisMappings") != null) {
                cacheManager.getCache("obisMappings").put(model, mappings);
            }
        });

        log.info("✅ OBIS codes preloaded into 'obisMappings' cache grouped by model.");
    }

    @Cacheable(cacheNames = "obisMappings", key = "#model")
    public List<ObisCodeEntity> getMappingsByModel(String model) {
        return obisCodeRepository.findActiveByModel(model);
    }

    public ObisColumnDto getDescriptionAndColumnName(String obisCode, String model) {
        List<ObisCodeEntity> obisCodes = obisCodeRepository.findActiveByModel(model);
        Optional<ObisCodeEntity> optionalMapping = obisCodes.stream()
                .filter(o -> {
                    String code = o.getCode();
                    String dotNotation = extractObisCode(code);
                    if (dotNotation == null) dotNotation = code;
                    return code.equalsIgnoreCase(obisCode)
                            || dotNotation.equalsIgnoreCase(obisCode)
                            || dotNotation.replace(".", "").equalsIgnoreCase(obisCode.replace(".", ""));
                })
                .findFirst();

        if (optionalMapping.isEmpty()) {
            log.warn("OBIS mapping not found for obisCode {}: model : {}", obisCode, model);
            return new ObisColumnDto("", "");
        }

        ObisCodeEntity mapping = optionalMapping.get();
        String description = mapping.getDescription();

        String columnName = generateColumnNameFromDescription(description);

        return new ObisColumnDto(description, columnName);
    }

    private Double parseScaler(String scalerStr) {
        if (scalerStr == null || scalerStr.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(scalerStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse scaler value '{}': {}", scalerStr, e.getMessage());
            return null;
        }
    }

    private String extractObisCode(String code) {
        if (code == null) return null;
        if (code.contains(";")) {
            String[] parts = code.split(";");
            if (parts.length >= 2) {
                return parts[1].trim();
            }
        }
        return null;
    }

    private String generateColumnNameFromDescription(String description) {
        if (description == null) return null;

        return description
                .replace("register", "")
                .replace("Register", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9_]", "")
                .trim()
                .toLowerCase();
    }
}
