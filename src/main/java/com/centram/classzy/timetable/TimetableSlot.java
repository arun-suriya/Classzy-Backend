package com.centram.classzy.timetable;

import java.time.LocalTime;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One row of the day structure: a teaching period, a break or lunch. */
@Entity
@Table(name = "timetable_slots")
public class TimetableSlot extends TenantEntity {
    public String name;
    @Column(name = "slot_type")
    public String slotType = "TEACHING";   // TEACHING, BREAK, LUNCH, OTHER
    public LocalTime startTime;
    public LocalTime endTime;
    public boolean active = true;
}
