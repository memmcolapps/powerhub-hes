package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "obis_codes")
@Getter
@Setter
public class ObisCodeEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_integration_id", nullable = false)
    private MeterIntegration meterIntegration;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "obis_type", nullable = false, length = 20)
    private String obisType = "REAL_TIME";

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "scaler", length = 50)
    private String scaler;

    @Column(name = "multiply_by", length = 50)
    private String multiplyBy;

    @Column(name = "action_type", length = 50)
    private String actionType;
}
