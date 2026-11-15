package com.centram.classzy.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.centram.classzy.institution.InstitutionEnums.PlanType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "institution_id", nullable = false)
    private Long institutionId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;
    @Enumerated(EnumType.STRING)
    private PlanType planType;
    @Column(nullable = false)
    private String method = "BANK_TRANSFER";
    private String reference;
    private String note;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public void setInstitutionId(Long v) { this.institutionId = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate v) { this.paidOn = v; }
    public PlanType getPlanType() { return planType; }
    public void setPlanType(PlanType v) { this.planType = v; }
    public String getMethod() { return method; }
    public void setMethod(String v) { this.method = v; }
    public String getReference() { return reference; }
    public void setReference(String v) { this.reference = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
}
