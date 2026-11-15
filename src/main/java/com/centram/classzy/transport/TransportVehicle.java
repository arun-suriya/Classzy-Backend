package com.centram.classzy.transport;

import java.time.LocalDate;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "transport_vehicles")
public class TransportVehicle extends TenantEntity {
    public String vehicleNo;
    public String registrationNo;
    public String vehicleType = "BUS";
    public String make;
    public String model;
    public Integer manufactureYear;
    public int seatingCapacity;
    public Integer standingCapacity;
    public String fuelType = "DIESEL";
    public LocalDate insuranceExpiry;
    public LocalDate pucExpiry;
    public String status = "ACTIVE";
    public Long routeId;
    public Long driverId;
    public String remarks;
}
