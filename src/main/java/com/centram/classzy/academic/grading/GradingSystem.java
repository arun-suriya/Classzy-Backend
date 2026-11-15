package com.centram.classzy.academic.grading;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "grading_systems")
public class GradingSystem extends TenantEntity {

    private String name;
    private String description;
    private boolean active = true;
    private String gradeType = "GRADE_POINT";
    private int totalMarks = 100;
    private Integer classFrom;
    private Integer classTo;
    private int passPercentage = 33;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getGradeType() { return gradeType; }
    public void setGradeType(String gradeType) { this.gradeType = gradeType; }
    public int getTotalMarks() { return totalMarks; }
    public void setTotalMarks(int totalMarks) { this.totalMarks = totalMarks; }
    public Integer getClassFrom() { return classFrom; }
    public void setClassFrom(Integer classFrom) { this.classFrom = classFrom; }
    public Integer getClassTo() { return classTo; }
    public void setClassTo(Integer classTo) { this.classTo = classTo; }
    public int getPassPercentage() { return passPercentage; }
    public void setPassPercentage(int passPercentage) { this.passPercentage = passPercentage; }
}
