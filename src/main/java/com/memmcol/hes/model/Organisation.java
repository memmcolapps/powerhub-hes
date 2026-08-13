package com.memmcol.hes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

@Entity
@Table(name = "organisations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_organisations_email", columnNames = {"email"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organisation extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "dial_code", nullable = false, length = 10)
    private String dialCode;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "status_reason", length = 1000)
    private String statusReason;

    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";
}
