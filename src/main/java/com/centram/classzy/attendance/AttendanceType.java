package com.centram.classzy.attendance;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendance_types")
public class AttendanceType extends TenantEntity {
    public String name;
    public String code;
    public String category;          // REGULAR, LEAVE, NON_WORKING, SPECIAL
    public String countsAs;          // PRESENT, ABSENT, PRESENT_LATE, PRESENT_HALF, LEAVE, NON_WORKING, EXCUSED
    public String color = "#6C757D";
    @Column(name = "is_default")
    public boolean defaultType;
    public boolean active = true;
    public String description;
    public int sortOrder;
}
