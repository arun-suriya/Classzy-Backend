package com.centram.classzy.timetable;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** One row per institution: General Rules + Conflict Rules tabs. */
@Entity
@Table(name = "timetable_rule_settings")
public class TimetableRuleSettings {
    @Id
    @Column(name = "institution_id")
    public Long institutionId;
    public int maxPeriodsPerDay = 8;
    public int maxConsecutiveTeacher = 4;
    public int minBreakBetween = 10;
    public int maxConsecutiveSubject = 2;
    public boolean allowDoublePeriods = true;
    public boolean allowFreePeriods = true;
    public boolean preferMorningCore;
    public boolean genAvoidTeacherConflicts = true;
    public boolean genAvoidRoomConflicts = true;
    public boolean genDistributeEvenly = true;
    public boolean genWeeklyFrequency = true;
    public boolean genWorkingHours = true;
    public boolean genPreferredSlots = true;
    public boolean genManualAdjust = true;
    public boolean genAvoidConsecutive = true;
    public boolean genDailyDiversity = true;
    public boolean genTeacherWorkload = true;
    public boolean genAiSuggestions;
    public String rulesDescription;
    public String onTeacherConflict = "SUGGEST_ALTERNATIVE";
    public String onRoomConflict = "SUGGEST_ALTERNATIVE";
    public String onSubjectConflict = "NEXT_SLOT";
    public String onLimitExceeded = "WARN_APPROVAL";
    public String conflictOrder = "TEACHER_AVAILABILITY,CLASS_SUBJECT_REQUIREMENTS,ROOM_AVAILABILITY,SUBJECT_SEQUENCE,PREFERRED_SLOTS,EQUAL_DISTRIBUTION";
    public boolean advAutoResolve = true;
    public boolean advReport = true;
    public boolean advHighlight = true;
    public boolean advManualOverride = true;
    public Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
