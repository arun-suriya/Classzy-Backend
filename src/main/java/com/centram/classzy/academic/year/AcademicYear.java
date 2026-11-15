package com.centram.classzy.academic.year;

import java.time.LocalDate;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "academic_years")
public class AcademicYear extends TenantEntity {

    private String name;
    private String shortName;
    private LocalDate startDate;
    private LocalDate endDate;

    @Column(name = "is_current")
    private boolean currentYear;

    private boolean active = true;
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isCurrentYear() { return currentYear; }
    public void setCurrentYear(boolean currentYear) { this.currentYear = currentYear; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
