package com.centram.classzy.attendance;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendance_templates")
public class AttendanceTemplate extends TenantEntity {
    public String name;
    public String body;
    public boolean active = true;
}
