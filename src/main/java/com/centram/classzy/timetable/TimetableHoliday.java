package com.centram.classzy.timetable;

import java.time.LocalDate;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "timetable_holidays")
public class TimetableHoliday extends TenantEntity {
    public String name;
    public String holidayType = "NATIONAL";   // NATIONAL, FESTIVAL, VACATION, SCHOOL_EVENT, OTHER
    public LocalDate startDate;
    public LocalDate endDate;
    public boolean active = true;
}
