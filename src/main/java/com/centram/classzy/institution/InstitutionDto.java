package com.centram.classzy.institution;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import com.centram.classzy.institution.InstitutionEnums.GradingSystemType;
import com.centram.classzy.institution.InstitutionEnums.Lifecycle;
import com.centram.classzy.institution.InstitutionEnums.ModuleCode;
import com.centram.classzy.institution.InstitutionEnums.PlanType;
import com.centram.classzy.institution.InstitutionEnums.Status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class InstitutionDto {
    private InstitutionDto() {
    }

    /**
     * Body for create and update. With draft=true only the name is required, so a half-filled form can be saved;
     * otherwise everything the form marks as required must be present.
     */
    public record Input(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 40) String institutionType,
            @Size(max = 200) String affiliation,
            Integer establishmentYear,
            @Size(max = 2000) String description,
            @Size(max = 150) String contactName,
            @Size(max = 100) String designation,
            @Size(max = 200) String email,
            @Size(max = 8) String phoneCode,
            @Size(max = 20) String phone,
            @Size(max = 150) String altContactName,
            @Size(max = 100) String altDesignation,
            @Size(max = 200) String altEmail,
            @Size(max = 8) String altPhoneCode,
            @Size(max = 20) String altPhone,
            @Size(max = 500) String address,
            @Size(max = 80) String country,
            @Size(max = 80) String state,
            @Size(max = 80) String city,
            @Size(max = 12) String pinCode,
            Integer academicStartMonth,
            @Size(max = 20) String dateFormat,
            @Size(max = 80) String timeZone,
            @Size(max = 40) String language,
            GradingSystemType gradingSystem,
            @Size(max = 30) String currency,
            Set<DayOfWeek> workingDays,
            LocalTime startTime,
            LocalTime endTime,
            PlanType planType,
            LocalDate subscriptionStart,
            LocalDate subscriptionEnd,
            Integer expectedStudents,
            Integer expectedTeachers,
            Set<ModuleCode> modules,
            boolean draft) {
    }

    /** One row of the institutions list. */
    public record Summary(
            Long id,
            String name,
            @Size(max = 40) String institutionType,
            Status status,
            String contactName,
            String email,
            String city,
            String state,
            PlanType planType,
            LocalDate subscriptionEnd,
            long studentCount,
            long teacherCount,
            boolean hasLogo,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Everything the edit form needs. */
    public record Detail(
            Long id,
            Status status,
            Lifecycle lifecycle,
            String name,
            @Size(max = 40) String institutionType,
            String affiliation,
            Integer establishmentYear,
            String description,
            String contactName,
            String designation,
            String email,
            String phoneCode,
            String phone,
            String altContactName,
            String altDesignation,
            String altEmail,
            String altPhoneCode,
            String altPhone,
            String address,
            String country,
            String state,
            String city,
            String pinCode,
            int academicStartMonth,
            String dateFormat,
            String timeZone,
            String language,
            GradingSystemType gradingSystem,
            String currency,
            Set<DayOfWeek> workingDays,
            LocalTime startTime,
            LocalTime endTime,
            PlanType planType,
            LocalDate subscriptionStart,
            LocalDate subscriptionEnd,
            Integer expectedStudents,
            Integer expectedTeachers,
            Set<ModuleCode> modules,
            boolean hasLogo,
            boolean hasBanner,
            long studentCount,
            long teacherCount,
            Instant createdAt,
            Instant updatedAt) {
    }
}
