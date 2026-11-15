package com.centram.classzy.academic.subject;

import java.util.HashSet;
import java.util.Set;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "subjects")
public class Subject extends TenantEntity {

    private Long academicYearId;
    private String name;
    private String code;
    private String category;
    private boolean active = true;
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subject_grade_levels", joinColumns = @JoinColumn(name = "subject_id"))
    @Column(name = "grade_level")
    @BatchSize(size = 50)
    private Set<Integer> gradeLevels = new HashSet<>();

    public Long getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(Long academicYearId) { this.academicYearId = academicYearId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<Integer> getGradeLevels() { return gradeLevels; }
}
