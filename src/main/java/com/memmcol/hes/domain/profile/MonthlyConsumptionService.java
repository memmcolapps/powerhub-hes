package com.memmcol.hes.domain.profile;

import com.memmcol.hes.dto.MonthlyConsumptionDTO;
import com.memmcol.hes.entities.MonthlyBillingEntity;
import com.memmcol.hes.repository.MonthlyBillingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyConsumptionService {

    private final MonthlyBillingRepository billingRepo;

    /**
     * Calculate consumption for a meter between previous month and current month.
     */
    public MonthlyConsumptionDTO calculateMonthlyConsumption(String meterSerial, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate prevMonthStart = month.minusMonths(1).atDay(1);

        // Fetch billing records (previous and current)
        MonthlyBillingEntity prev = billingRepo
                .findByMeterSerialAndEntryTimestamp(meterSerial, prevMonthStart.atStartOfDay())
                .orElseThrow(() -> new IllegalStateException("No billing record for " + prevMonthStart));

        MonthlyBillingEntity curr = billingRepo
                .findByMeterSerialAndEntryTimestamp(meterSerial, monthStart.atStartOfDay())
                .orElseThrow(() -> new IllegalStateException("No billing record for " + monthStart));

        // Calculate consumption
        Double prevVal = prev.getTotalActiveEnergy();
        Double currVal = curr.getTotalActiveEnergy();

        BigDecimal consumptionBd = null;
        if (currVal != null && prevVal != null) {
            consumptionBd = BigDecimal.valueOf(currVal)
                    .subtract(BigDecimal.valueOf(prevVal))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Double consumption = (consumptionBd == null) ? null : consumptionBd.doubleValue();

        MonthlyConsumptionDTO consumptionDTO = MonthlyConsumptionDTO.builder()
                .meterSerial(meterSerial)
                .monthStart(monthStart)
                .prevValueKwh(prevVal)
                .currValueKwh(currVal)
                .consumptionKwh(consumption)
                .build();

        log.info("MonthlyConsumption calculated: {}", consumptionDTO.toString());
        return consumptionDTO;
    }

}
