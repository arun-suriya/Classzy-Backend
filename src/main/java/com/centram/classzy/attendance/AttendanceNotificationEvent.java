package com.centram.classzy.attendance;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendance_notification_events")
public class AttendanceNotificationEvent extends TenantEntity {
    public String name;
    public String triggerCode;       // ABSENT_AT_DAY_END, LATE_CHECK_IN, HALF_DAY, DAY_END_SUMMARY, LOW_ATTENDANCE
    public String recipients;        // comma separated: PARENT,CLASS_TEACHER,PRINCIPAL,ADMIN
    public String channels;          // comma separated: EMAIL,SMS,APP
    public Long templateId;
    public String customMessage;
    public boolean active = true;
}
