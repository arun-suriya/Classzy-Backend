package com.centram.classzy.attendance;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** One row per institution. Plain data holder: fields are read and written by the service. */
@Entity
@Table(name = "attendance_settings")
public class AttendanceSettings {

    @Id
    @Column(name = "institution_id")
    public Long institutionId;

    public String attendanceMode = "DAILY";
    public String defaultMethod = "MANUAL";
    public String defaultMark = "PRESENT";
    public int lateAfterMinutes = 15;
    public int halfDayMinutes = 180;

    public boolean biometric = true;
    public boolean rfid = true;
    public boolean mobileApp = true;
    public boolean gps = true;
    public boolean offlineMode;
    public boolean autoSync = true;

    public boolean parentView = true;
    public boolean studentView = true;
    public boolean remarksEnabled = true;
    public boolean adjustmentEnabled = true;

    public int minAttendancePct = 75;
    public boolean countPresent = true;
    public boolean countLate = true;
    public boolean countHalfDay = true;
    public boolean countOnLeave;
    public boolean countSickLeave;
    public boolean countExcused;
    public boolean excludeHolidays = true;
    public boolean excludeWeekOffs = true;
    public int gracePeriodMinutes = 5;
    public LocalTime autoAbsentTime = LocalTime.of(10, 0);

    public Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
