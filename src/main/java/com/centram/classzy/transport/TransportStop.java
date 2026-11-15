package com.centram.classzy.transport;

import java.time.LocalTime;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "transport_stops")
public class TransportStop extends TenantEntity {
    public Long routeId;
    public String name;
    public String code;
    public int sequenceNo;
    public Double latitude;
    public Double longitude;
    public String address;
    public LocalTime arrivalTime;
    public LocalTime departureTime;
    public int radiusMeters = 100;
    public String stopType = "PICKUP_DROP";
    public String status = "ACTIVE";
}
