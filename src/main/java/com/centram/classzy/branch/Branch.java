package com.centram.classzy.branch;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "institution_id", nullable = false, updatable = false)
    private Long institutionId;
    @Column(nullable = false)
    private String name;
    private String code;
    @Column(name = "branch_type", nullable = false)
    private String branchType = "BRANCH";           // CAMPUS or BRANCH
    @Column(name = "is_main", nullable = false)
    private boolean mainBranch;
    @Column(nullable = false)
    private String status = "ACTIVE";               // ACTIVE or INACTIVE
    private String headName;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String state;
    private String pinCode;
    private Integer establishmentYear;
    private String notes;
    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public void setInstitutionId(Long v) { this.institutionId = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getBranchType() { return branchType; }
    public void setBranchType(String v) { this.branchType = v; }
    public boolean isMainBranch() { return mainBranch; }
    public void setMainBranch(boolean v) { this.mainBranch = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getHeadName() { return headName; }
    public void setHeadName(String v) { this.headName = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getPinCode() { return pinCode; }
    public void setPinCode(String v) { this.pinCode = v; }
    public Integer getEstablishmentYear() { return establishmentYear; }
    public void setEstablishmentYear(Integer v) { this.establishmentYear = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
    public Instant getUpdatedAt() { return updatedAt; }
}
