package com.centram.classzy.attendance;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendance_rules")
public class AttendanceRule extends TenantEntity {
    public String name;
    public String description;
    public String appliesTo = "ALL_STUDENTS";
    public int priority = 1;
    public boolean active = true;
    public String conditionField;
    public String conditionOperator;
    public String conditionValue;
    public String actionTypeCode;
    public boolean notifyParent;
    public boolean includeInReports = true;
}
