package com.memmcol.hes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organisation extends AuditableEntity {

    @Column(name = "business_name", nullable = false)
    private String businessName;
}
