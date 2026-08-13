package com.memmcol.hes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileChannel2Reading {

    private Long id;

    private String meterSerial;

    private String modelNumber;

    private int entryIndex;

    private LocalDateTime entryTimestamp;

    private Double exportActiveEnergy;

    private Double importActiveEnergy;

    private String rawData;

    private LocalDateTime receivedAt;
}
