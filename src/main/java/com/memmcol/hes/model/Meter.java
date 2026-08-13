package com.memmcol.hes.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "meters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meter extends AuditableEntity {

    @Column(name = "meter_number", nullable = false, unique = true)
    private String meterNumber;

    @Column(name = "sim_number", nullable = false, unique = true)
    private String simNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_integration_id", nullable = false)
    private MeterIntegration meterIntegration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @Column(name = "old_sgc", length = 10)
    private String oldSgc;

    @Column(name = "new_sgc", length = 10)
    private String newSgc;

    @Column(name = "old_krn")
    private Integer oldKrn;

    @Column(name = "new_krn")
    private Integer newKrn;

    @Column(name = "old_tariff_index")
    private Integer oldTariffIndex;

    @Column(name = "new_tariff_index")
    private Integer newTariffIndex;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";
}
