package com.centram.classzy.timetable;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** One row per institution: General Settings tab + half-day settings. */
@Entity
@Table(name = "timetable_settings")
public class TimetableSettings {
    @Id
    @Column(name = "institution_id")
    public Long institutionId;
    public String configName = "Default Period Config";
    public Long academicYearId;
    public String description;
    public String timeFormat = "H12";
    public String periodNaming = "PERIOD_N";
    public int defaultDuration = 45;
    public boolean allowVariableDuration = true;
    public boolean allowZeroPeriod;
    public boolean allowExtraPeriod;
    public boolean enableBreaks = true;
    public boolean enableLunchBreak = true;
    public boolean showPeriodNumber = true;
    public boolean halfDayEnabled;
    public LocalTime halfDayEndTime = LocalTime.of(12, 0);
    public Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
