package com.centram.classzy.academic.grading;

import java.math.BigDecimal;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "grades")
public class Grade extends TenantEntity {

    private Long gradingSystemId;
    private String grade;
    private Integer marksFrom;
    private Integer marksTo;
    private BigDecimal gradePoint;
    private String description;

    public Long getGradingSystemId() { return gradingSystemId; }
    public void setGradingSystemId(Long gradingSystemId) { this.gradingSystemId = gradingSystemId; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Integer getMarksFrom() { return marksFrom; }
    public void setMarksFrom(Integer marksFrom) { this.marksFrom = marksFrom; }
    public Integer getMarksTo() { return marksTo; }
    public void setMarksTo(Integer marksTo) { this.marksTo = marksTo; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public void setGradePoint(BigDecimal gradePoint) { this.gradePoint = gradePoint; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
