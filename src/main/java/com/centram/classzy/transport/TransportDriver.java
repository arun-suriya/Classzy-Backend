package com.centram.classzy.transport;

import java.time.LocalDate;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "transport_drivers")
public class TransportDriver extends TenantEntity {
    public String code;
    public String name;
    public String mobile;
    public LocalDate dateOfBirth;
    public String gender;
    public String address;
    public String licenseNo;
    public LocalDate licenseExpiry;
    public String licenseType = "HMV";
    public Integer experienceYears;
    public String emergencyName;
    public String emergencyPhone;
    public String status = "ACTIVE";
    public String remarks;
}
