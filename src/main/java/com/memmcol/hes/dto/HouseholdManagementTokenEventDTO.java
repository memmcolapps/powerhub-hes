package com.memmcol.hes.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdManagementTokenEventDTO {
    private String meterSerial;
    private String meterModel;
    private String profileObis;
    private Integer eventCode;
    private LocalDateTime eventTime;
    /** Raw DLMS value before domain lookup (manage-token-type code). */
    private Object manageTokenTypeRaw;
    private String manageToken;
}
