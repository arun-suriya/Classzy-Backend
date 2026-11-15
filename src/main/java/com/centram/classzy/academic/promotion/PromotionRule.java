package com.centram.classzy.academic.promotion;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotion_rules")
public class PromotionRule extends TenantEntity {

    private Long academicYearId;
    private String name;
    private String fromClass;
    private String toClass;

    @Enumerated(EnumType.STRING)
    private CriteriaType criteriaType;

    private Integer minValue;

    @Enumerated(EnumType.STRING)
    private SubjectScope subjectScope;

    private String criteria;
    private String additionalConditions;
    private boolean active = true;

    public Long getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(Long academicYearId) { this.academicYearId = academicYearId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFromClass() { return fromClass; }
    public void setFromClass(String fromClass) { this.fromClass = fromClass; }
    public String getToClass() { return toClass; }
    public void setToClass(String toClass) { this.toClass = toClass; }
    public CriteriaType getCriteriaType() { return criteriaType; }
    public void setCriteriaType(CriteriaType criteriaType) { this.criteriaType = criteriaType; }
    public Integer getMinValue() { return minValue; }
    public void setMinValue(Integer minValue) { this.minValue = minValue; }
    public SubjectScope getSubjectScope() { return subjectScope; }
    public void setSubjectScope(SubjectScope subjectScope) { this.subjectScope = subjectScope; }
    public String getCriteria() { return criteria; }
    public void setCriteria(String criteria) { this.criteria = criteria; }
    public String getAdditionalConditions() { return additionalConditions; }
    public void setAdditionalConditions(String additionalConditions) { this.additionalConditions = additionalConditions; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
