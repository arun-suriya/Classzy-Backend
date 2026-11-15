package com.centram.classzy.academic.promotion;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_progressions")
public class StudentProgression extends TenantEntity {

    private Long academicYearId;
    private Long studentId;
    private String nextClass;

    @Enumerated(EnumType.STRING)
    private ProgressionStatus status;

    private String remarks;

    public Long getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(Long academicYearId) { this.academicYearId = academicYearId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getNextClass() { return nextClass; }
    public void setNextClass(String nextClass) { this.nextClass = nextClass; }
    public ProgressionStatus getStatus() { return status; }
    public void setStatus(ProgressionStatus status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
