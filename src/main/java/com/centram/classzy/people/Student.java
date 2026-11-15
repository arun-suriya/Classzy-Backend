package com.centram.classzy.people;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "students")
public class Student extends TenantEntity {

    private String admissionNo;
    private String fullName;
    private Long schoolClassId;
    private Long sectionId;
    private boolean active = true;

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Long getSchoolClassId() { return schoolClassId; }
    public void setSchoolClassId(Long schoolClassId) { this.schoolClassId = schoolClassId; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
