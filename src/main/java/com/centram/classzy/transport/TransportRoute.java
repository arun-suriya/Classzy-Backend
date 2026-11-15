package com.centram.classzy.transport;


import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "transport_routes")
public class TransportRoute extends TenantEntity {
    public String name;
    public String code;
    public String startLocation;
    public String endLocation;
    public Double distanceKm;
    public Integer durationMinutes;
    public String status = "ACTIVE";
    public String description;
}
