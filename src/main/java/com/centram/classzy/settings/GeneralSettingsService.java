package com.centram.classzy.settings;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.institution.Institution;
import com.centram.classzy.institution.InstitutionEnums.GradingSystemType;
import com.centram.classzy.institution.InstitutionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** General settings of the institution being managed; stored on the institution profile itself. */
@Service
@Transactional
public class GeneralSettingsService {

    public record General(
            String name, String institutionType, String affiliation, Integer establishmentYear, String description,
            String email, String phoneCode, String phone, String address, String country, String state, String city, String pinCode,
            String dateFormat, String timeZone, String language, String currency, int academicStartMonth,
            List<String> workingDays, String startTime, String endTime, String gradingSystem) {
    }

    private static final Set<String> DATE_FORMATS = Set.of("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD", "DD-MM-YYYY", "DD MMM YYYY");

    private final CurrentUser current;
    private final InstitutionRepository institutions;
    private final AuditService audit;

    public GeneralSettingsService(CurrentUser current, InstitutionRepository institutions, AuditService audit) {
        this.current = current;
        this.institutions = institutions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public General get() {
        return toDto(load());
    }

    public General update(General g) {
        Institution i = load();
        Map<String, String> err = new LinkedHashMap<>();
        if (g.email() != null && !g.email().isBlank() && !g.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) err.put("email", "Enter a valid email address.");
        if (g.phone() != null && !g.phone().isBlank() && !g.phone().matches("[0-9 +()-]{6,20}")) err.put("phone", "Enter a valid phone number.");
        if (g.pinCode() != null && g.pinCode().length() > 12) err.put("pinCode", "PIN / ZIP code is too long.");
        if (g.establishmentYear() != null && (g.establishmentYear() < 1800 || g.establishmentYear() > java.time.Year.now().getValue())) err.put("establishmentYear", "Enter a valid year.");
        if (g.dateFormat() == null || !DATE_FORMATS.contains(g.dateFormat())) err.put("dateFormat", "Choose a date format.");
        if (g.timeZone() == null || g.timeZone().isBlank()) err.put("timeZone", "Choose a time zone.");
        if (g.language() == null || g.language().isBlank()) err.put("language", "Choose a language.");
        if (g.currency() == null || g.currency().isBlank()) err.put("currency", "Choose a currency.");
        if (g.academicStartMonth() < 1 || g.academicStartMonth() > 12) err.put("academicStartMonth", "Choose a month.");
        GradingSystemType grading = null;
        try {
            if (g.gradingSystem() != null && !g.gradingSystem().isBlank()) grading = GradingSystemType.valueOf(g.gradingSystem());
        } catch (IllegalArgumentException e) {
            err.put("gradingSystem", "Choose a grading system.");
        }
        Set<DayOfWeek> days = new TreeSet<>();
        try {
            for (String d : g.workingDays() == null ? List.<String>of() : g.workingDays()) days.add(DayOfWeek.valueOf(d));
        } catch (IllegalArgumentException e) {
            err.put("workingDays", "Invalid working day.");
        }
        if (days.isEmpty()) err.putIfAbsent("workingDays", "Select at least one working day.");
        LocalTime start = null;
        LocalTime end = null;
        try {
            start = LocalTime.parse(g.startTime());
            end = LocalTime.parse(g.endTime());
            if (!end.isAfter(start)) err.put("endTime", "End time must be after start time.");
        } catch (Exception e) {
            err.put("startTime", "Enter valid start and end times.");
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        i.setAffiliation(blank(g.affiliation()));
        i.setEstablishmentYear(g.establishmentYear());
        i.setDescription(blank(g.description()));
        i.setEmail(blank(g.email()));
        i.setPhoneCode(blank(g.phoneCode()));
        i.setPhone(blank(g.phone()));
        i.setAddress(blank(g.address()));
        i.setCountry(blank(g.country()));
        i.setState(blank(g.state()));
        i.setCity(blank(g.city()));
        i.setPinCode(blank(g.pinCode()));
        i.setDateFormat(g.dateFormat());
        i.setTimeZone(g.timeZone());
        i.setLanguage(g.language());
        i.setCurrency(g.currency());
        i.setAcademicStartMonth(g.academicStartMonth());
        i.setStartTime(start);
        i.setEndTime(end);
        i.setGradingSystem(grading);
        i.getWorkingDays().clear();
        i.getWorkingDays().addAll(days);
        i.touch();
        institutions.save(i);
        audit.record("GENERAL_SETTINGS_UPDATED", "Institution", i.getId(), i.getId(), "General settings updated for " + i.getName());
        return toDto(i);
    }

    private Institution load() {
        return institutions.findById(current.institutionId()).orElseThrow(() -> ApiException.notFound("Institution"));
    }

    private General toDto(Institution i) {
        return new General(i.getName(), i.getInstitutionType(), i.getAffiliation(), i.getEstablishmentYear(), i.getDescription(),
                i.getEmail(), i.getPhoneCode(), i.getPhone(), i.getAddress(), i.getCountry(), i.getState(), i.getCity(), i.getPinCode(),
                i.getDateFormat(), i.getTimeZone(), i.getLanguage(), i.getCurrency(), i.getAcademicStartMonth(),
                i.getWorkingDays().stream().sorted().map(Enum::name).toList(),
                i.getStartTime() == null ? null : i.getStartTime().toString().substring(0, 5),
                i.getEndTime() == null ? null : i.getEndTime().toString().substring(0, 5),
                i.getGradingSystem() == null ? null : i.getGradingSystem().name());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
