package com.centram.classzy.timetable;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "timetable_rule_priorities")
public class RulePriority extends TenantEntity {
    public String code;
    public String name;
    @Column(name = "position")
    public int position;
    public boolean enabled = true;
}
