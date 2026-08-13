package com.memmcol.hes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

@Entity
@Table(name = "meter_integrations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_meter_integration_manufacturer_model", columnNames = {"manufacturer", "model"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterIntegration extends AuditableEntity {

    @Column(name = "manufacturer", nullable = false)
    private String manufacturer;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "class", nullable = false)
    private String meterClass;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "protocol", nullable = false)
    private String protocol;

    @Column(name = "authentication_type", nullable = false)
    private String authenticationType;

    @Column(name = "password")
    private String password;

    @Column(name = "serial", length = 100)
    private String serial;

    @Column(name = "multiplier", length = 50)
    private String multiplier;

    @Column(name = "security_policy", length = 100)
    private String securityPolicy;

    @Column(name = "auth_mechanism", length = 100)
    private String authMechanism;

    @Column(name = "encryption_key", length = 255)
    private String encryptionKey;

    @Column(name = "master_key", length = 255)
    private String masterKey;

    @Column(name = "global_broadcast_encryption_key", length = 255)
    private String globalBroadcastEncryptionKey;

    @Column(name = "destination_address", length = 255)
    private String destinationAddress;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "status_reason", length = 1000)
    private String statusReason;
}
