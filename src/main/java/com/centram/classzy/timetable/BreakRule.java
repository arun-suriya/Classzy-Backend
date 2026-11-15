package com.centram.classzy.timetable;

import java.time.LocalTime;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "break_rules")
public class BreakRule extends TenantEntity {
    public String name;
    public Long breakTypeId;
    public String appliesTo = "ALL_CLASSES";   // ALL_CLASSES, GRADE_RANGE
    public Integer classFrom;
    public Integer classTo;
    public LocalTime startTime;
    public int durationMinutes;
    public String frequency = "DAILY";         // DAILY, SELECTED_DAYS
    public String days;                        // comma separated weekdays when SELECTED_DAYS
    public boolean active = true;
    public String description;
}
