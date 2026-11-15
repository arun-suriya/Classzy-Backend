package com.centram.classzy.timetable;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "break_types")
public class BreakType extends TenantEntity {
    public String name;
    public String code;
    public int defaultDuration;
    public String color = "#4CAF50";
    public String applicableFor = "TEACHING_PERIODS";   // comma separated
    public boolean active = true;
    public String description;
}
