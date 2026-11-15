package com.centram.classzy.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import com.centram.classzy.institution.InstitutionEnums.GradingSystemType;
import com.centram.classzy.institution.InstitutionEnums.Lifecycle;
import com.centram.classzy.institution.InstitutionEnums.ModuleCode;
import com.centram.classzy.institution.InstitutionEnums.PlanType;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.DayOfWeek;

@Entity
@Table(name = "institutions")
public class Institution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Code of a row in institution_types (SCHOOL, COLLEGE, ...). */
    @Column(name = "institution_type", nullable = false)
    private String institutionType = "SCHOOL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Lifecycle lifecycle = Lifecycle.ACTIVE;

    private String affiliation;
    private Integer establishmentYear;
    private String description;

    private String contactName;
    private String designation;
    private String email;
    private String phoneCode;
    private String phone;

    @Column(name = "alt_contact_name")
    private String altContactName;
    @Column(name = "alt_designation")
    private String altDesignation;
    @Column(name = "alt_email")
    private String altEmail;
    @Column(name = "alt_phone_code")
    private String altPhoneCode;
    @Column(name = "alt_phone")
    private String altPhone;

    private String address;
    private String country = "India";
    private String state;
    private String city;
    private String pinCode;

    @Column(name = "academic_start_month", nullable = false)
    private int academicStartMonth = 4;
    private String dateFormat = "DD/MM/YYYY";
    private String timeZone = "(GMT+05:30) India Standard Time";
    private String language = "English";

    @Enumerated(EnumType.STRING)
    private GradingSystemType gradingSystem;
    private String currency = "INR (₹)";
    private LocalTime startTime = LocalTime.of(8, 0);
    private LocalTime endTime = LocalTime.of(15, 0);

    @Enumerated(EnumType.STRING)
    private PlanType planType;
    @Column(name = "subscription_start")
    private LocalDate subscriptionStart;
    @Column(name = "subscription_end")
    private LocalDate subscriptionEnd;
    private Integer expectedStudents;
    private Integer expectedTeachers;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "institution_modules", joinColumns = @JoinColumn(name = "institution_id"))
    @Column(name = "module_code")
    @Enumerated(EnumType.STRING)
    private Set<ModuleCode> modules = EnumSet.noneOf(ModuleCode.class);

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "institution_working_days", joinColumns = @JoinColumn(name = "institution_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> workingDays = EnumSet.noneOf(DayOfWeek.class);

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant archivedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- accessors ----
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getInstitutionType() { return institutionType; }
    public void setInstitutionType(String v) { this.institutionType = v; }
    public Lifecycle getLifecycle() { return lifecycle; }
    public void setLifecycle(Lifecycle v) { this.lifecycle = v; }
    public String getAffiliation() { return affiliation; }
    public void setAffiliation(String v) { this.affiliation = v; }
    public Integer getEstablishmentYear() { return establishmentYear; }
    public void setEstablishmentYear(Integer v) { this.establishmentYear = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getContactName() { return contactName; }
    public void setContactName(String v) { this.contactName = v; }
    public String getDesignation() { return designation; }
    public void setDesignation(String v) { this.designation = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPhoneCode() { return phoneCode; }
    public void setPhoneCode(String v) { this.phoneCode = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getAltContactName() { return altContactName; }
    public void setAltContactName(String v) { this.altContactName = v; }
    public String getAltDesignation() { return altDesignation; }
    public void setAltDesignation(String v) { this.altDesignation = v; }
    public String getAltEmail() { return altEmail; }
    public void setAltEmail(String v) { this.altEmail = v; }
    public String getAltPhoneCode() { return altPhoneCode; }
    public void setAltPhoneCode(String v) { this.altPhoneCode = v; }
    public String getAltPhone() { return altPhone; }
    public void setAltPhone(String v) { this.altPhone = v; }
    public String getAddress() { return address; }
    public void setAddress(String v) { this.address = v; }
    public String getCountry() { return country; }
    public void setCountry(String v) { this.country = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getPinCode() { return pinCode; }
    public void setPinCode(String v) { this.pinCode = v; }
    public int getAcademicStartMonth() { return academicStartMonth; }
    public void setAcademicStartMonth(int v) { this.academicStartMonth = v; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String v) { this.dateFormat = v; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String v) { this.timeZone = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }
    public GradingSystemType getGradingSystem() { return gradingSystem; }
    public void setGradingSystem(GradingSystemType v) { this.gradingSystem = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime v) { this.startTime = v; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime v) { this.endTime = v; }
    public PlanType getPlanType() { return planType; }
    public void setPlanType(PlanType v) { this.planType = v; }
    public LocalDate getSubscriptionStart() { return subscriptionStart; }
    public void setSubscriptionStart(LocalDate v) { this.subscriptionStart = v; }
    public LocalDate getSubscriptionEnd() { return subscriptionEnd; }
    public void setSubscriptionEnd(LocalDate v) { this.subscriptionEnd = v; }
    public Integer getExpectedStudents() { return expectedStudents; }
    public void setExpectedStudents(Integer v) { this.expectedStudents = v; }
    public Integer getExpectedTeachers() { return expectedTeachers; }
    public void setExpectedTeachers(Integer v) { this.expectedTeachers = v; }
    public Set<ModuleCode> getModules() { return modules; }
    public Set<DayOfWeek> getWorkingDays() { return workingDays; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = Instant.now(); }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant v) { this.archivedAt = v; }
}
