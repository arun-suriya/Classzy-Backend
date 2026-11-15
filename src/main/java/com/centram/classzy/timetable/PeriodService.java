package com.centram.classzy.timetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.centram.classzy.academic.year.AcademicYearRepository;
import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.institution.Institution;
import com.centram.classzy.institution.InstitutionRepository;
import com.centram.classzy.timetable.TimetableDto.General;
import com.centram.classzy.timetable.TimetableDto.HolidayBody;
import com.centram.classzy.timetable.TimetableDto.HolidayView;
import com.centram.classzy.timetable.TimetableDto.SlotBody;
import com.centram.classzy.timetable.TimetableDto.SlotView;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Period Configuration: general settings, day structure (periods/breaks) and holidays. */
@Service
@Transactional
public class PeriodService {

    private static final Set<String> FORMATS = Set.of("H12", "H24");
    private static final Set<String> NAMING = Set.of("PERIOD_N", "P_N", "NUMBER");
    private static final Set<String> SLOT_TYPES = Set.of("TEACHING", "BREAK", "LUNCH", "OTHER");
    private static final Set<String> HOLIDAY_TYPES = Set.of("NATIONAL", "FESTIVAL", "VACATION", "SCHOOL_EVENT", "OTHER");

    private final CurrentUser current;
    private final TimetableBootstrap bootstrap;
    private final TimetableSettingsRepository settingsRepo;
    private final TimetableSlotRepository slots;
    private final TimetableHolidayRepository holidays;
    private final InstitutionRepository institutions;
    private final AcademicYearRepository years;
    private final AuditService audit;

    public PeriodService(CurrentUser current, TimetableBootstrap bootstrap, TimetableSettingsRepository settingsRepo,
                         TimetableSlotRepository slots, TimetableHolidayRepository holidays, InstitutionRepository institutions,
                         AcademicYearRepository years, AuditService audit) {
        this.current = current;
        this.bootstrap = bootstrap;
        this.settingsRepo = settingsRepo;
        this.slots = slots;
        this.holidays = holidays;
        this.institutions = institutions;
        this.years = years;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ general + working days

    public General getGeneral() {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        return toGeneral(settingsRepo.findById(inst).orElseThrow(), institution(inst));
    }

    public General updateGeneral(General g) {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        TimetableSettings s = settingsRepo.findById(inst).orElseThrow();
        Institution i = institution(inst);
        Map<String, String> err = new LinkedHashMap<>();
        if (trim(g.configName()).isEmpty()) err.put("configName", "Configuration name is required.");
        else if (g.configName().trim().length() > 80) err.put("configName", "Configuration name is too long.");
        if (g.academicYearId() != null && years.findByIdAndInstitutionId(g.academicYearId(), inst).isEmpty()) err.put("academicYearId", "Academic year not found.");
        if (g.description() != null && g.description().length() > 500) err.put("description", "Description can be at most 500 characters.");
        if (!FORMATS.contains(g.timeFormat())) err.put("timeFormat", "Choose a time format.");
        if (!NAMING.contains(g.periodNaming())) err.put("periodNaming", "Choose a naming format.");
        if (g.defaultDuration() < 5 || g.defaultDuration() > 240) err.put("defaultDuration", "Duration must be between 5 and 240 minutes.");
        LocalTime halfEnd = time(err, "halfDayEndTime", g.halfDayEndTime());
        LocalTime start = time(err, "startTime", g.startTime());
        LocalTime end = time(err, "endTime", g.endTime());
        if (start != null && end != null && !end.isAfter(start)) err.put("endTime", "End time must be after start time.");
        if (g.halfDayEnabled() && halfEnd != null && start != null && end != null && (!halfEnd.isAfter(start) || !halfEnd.isBefore(end))) {
            err.put("halfDayEndTime", "Half day end time must fall between the school start and end times.");
        }
        Set<DayOfWeek> days = new TreeSet<>();
        try {
            for (String d : g.workingDays() == null ? List.<String>of() : g.workingDays()) days.add(DayOfWeek.valueOf(d));
        } catch (IllegalArgumentException e) {
            err.put("workingDays", "Invalid working day.");
        }
        if (days.isEmpty()) err.putIfAbsent("workingDays", "Select at least one working day.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        s.configName = g.configName().trim();
        s.academicYearId = g.academicYearId();
        s.description = blank(g.description());
        s.timeFormat = g.timeFormat();
        s.periodNaming = g.periodNaming();
        s.defaultDuration = g.defaultDuration();
        s.allowVariableDuration = g.allowVariableDuration();
        s.allowZeroPeriod = g.allowZeroPeriod();
        s.allowExtraPeriod = g.allowExtraPeriod();
        s.enableBreaks = g.enableBreaks();
        s.enableLunchBreak = g.enableLunchBreak();
        s.showPeriodNumber = g.showPeriodNumber();
        s.halfDayEnabled = g.halfDayEnabled();
        s.halfDayEndTime = halfEnd;
        settingsRepo.save(s);
        i.getWorkingDays().clear();
        i.getWorkingDays().addAll(days);
        i.setStartTime(start);
        i.setEndTime(end);
        i.touch();
        institutions.save(i);
        audit.record("TIMETABLE_SETTINGS_UPDATED", "TimetableSettings", inst, inst, "Period configuration updated");
        return toGeneral(s, i);
    }

    private General toGeneral(TimetableSettings s, Institution i) {
        return new General(s.configName, s.academicYearId, s.description, s.timeFormat, s.periodNaming, s.defaultDuration,
                s.allowVariableDuration, s.allowZeroPeriod, s.allowExtraPeriod, s.enableBreaks, s.enableLunchBreak, s.showPeriodNumber,
                s.halfDayEnabled, TimetableDto.hhmm(s.halfDayEndTime), i.getWorkingDays().stream().sorted().map(Enum::name).toList(),
                TimetableDto.hhmm(i.getStartTime()), TimetableDto.hhmm(i.getEndTime()));
    }

    // ------------------------------------------------------------------ day structure

    public List<SlotView> listSlots() {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        return slots.findByInstitutionIdOrderByStartTimeAsc(inst).stream().map(SlotView::of).toList();
    }

    public SlotView createSlot(SlotBody b) {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        TimetableSlot s = new TimetableSlot();
        s.setInstitutionId(inst);
        applySlot(s, b, inst, 0L);
        s = slots.saveAndFlush(s);
        audit.record("TIMETABLE_SLOT_CREATED", "TimetableSlot", s.getId(), inst, "Period " + s.name + " created");
        return SlotView.of(s);
    }

    public SlotView updateSlot(Long id, SlotBody b) {
        Long inst = current.institutionId();
        TimetableSlot s = requireSlot(id, inst);
        applySlot(s, b, inst, id);
        s = slots.saveAndFlush(s);
        audit.record("TIMETABLE_SLOT_UPDATED", "TimetableSlot", id, inst, "Period " + s.name + " updated");
        return SlotView.of(s);
    }

    public void deleteSlot(Long id) {
        Long inst = current.institutionId();
        TimetableSlot s = requireSlot(id, inst);
        slots.delete(s);
        audit.record("TIMETABLE_SLOT_DELETED", "TimetableSlot", id, inst, "Period " + s.name + " deleted");
    }

    private TimetableSlot requireSlot(Long id, Long inst) {
        return slots.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Period"));
    }

    private void applySlot(TimetableSlot s, SlotBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Period name is required.");
        else if (name.length() > 60) err.put("name", "Period name is too long.");
        else if (slots.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A period with this name already exists.");
        if (!SLOT_TYPES.contains(b.slotType())) err.put("slotType", "Choose a type.");
        LocalTime start = time(err, "startTime", b.startTime());
        LocalTime end = time(err, "endTime", b.endTime());
        if (start != null && end != null) {
            if (!end.isAfter(start)) err.put("endTime", "End time must be after start time.");
            else if (b.active()) {
                for (TimetableSlot o : slots.findByInstitutionIdOrderByStartTimeAsc(inst)) {
                    boolean overlaps = o.active && !o.getId().equals(self) && start.isBefore(o.endTime) && end.isAfter(o.startTime);
                    if (overlaps) { err.put("startTime", "These times overlap with " + o.name + " (" + TimetableDto.hhmm(o.startTime) + " - " + TimetableDto.hhmm(o.endTime) + ")."); break; }
                }
            }
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        s.name = name;
        s.slotType = b.slotType();
        s.startTime = start;
        s.endTime = end;
        s.active = b.active();
    }

    // ------------------------------------------------------------------ holidays

    public List<HolidayView> listHolidays() {
        Long inst = current.institutionId();
        return holidays.findByInstitutionIdOrderByStartDateAsc(inst).stream().map(HolidayView::of).toList();
    }

    public HolidayView createHoliday(HolidayBody b) {
        Long inst = current.institutionId();
        TimetableHoliday h = new TimetableHoliday();
        h.setInstitutionId(inst);
        applyHoliday(h, b);
        h = holidays.saveAndFlush(h);
        audit.record("HOLIDAY_CREATED", "TimetableHoliday", h.getId(), inst, "Holiday " + h.name + " created");
        return HolidayView.of(h);
    }

    public HolidayView updateHoliday(Long id, HolidayBody b) {
        Long inst = current.institutionId();
        TimetableHoliday h = holidays.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Holiday"));
        applyHoliday(h, b);
        h = holidays.saveAndFlush(h);
        audit.record("HOLIDAY_UPDATED", "TimetableHoliday", id, inst, "Holiday " + h.name + " updated");
        return HolidayView.of(h);
    }

    public void deleteHoliday(Long id) {
        Long inst = current.institutionId();
        TimetableHoliday h = holidays.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Holiday"));
        holidays.delete(h);
        audit.record("HOLIDAY_DELETED", "TimetableHoliday", id, inst, "Holiday " + h.name + " deleted");
    }

    private void applyHoliday(TimetableHoliday h, HolidayBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Holiday name is required.");
        else if (name.length() > 80) err.put("name", "Holiday name is too long.");
        if (!HOLIDAY_TYPES.contains(b.holidayType())) err.put("holidayType", "Choose a holiday type.");
        LocalDate from = null;
        LocalDate to = null;
        try {
            from = LocalDate.parse(b.startDate());
            to = b.endDate() == null || b.endDate().isBlank() ? from : LocalDate.parse(b.endDate());
            if (to.isBefore(from)) err.put("endDate", "End date can't be before the start date.");
        } catch (Exception e) {
            err.put("startDate", "Enter a valid date.");
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        h.name = name;
        h.holidayType = b.holidayType();
        h.startDate = from;
        h.endDate = to;
        h.active = b.active();
    }

    // ------------------------------------------------------------------ helpers

    private Institution institution(Long id) {
        return institutions.findById(id).orElseThrow(() -> ApiException.notFound("Institution"));
    }

    private static LocalTime time(Map<String, String> err, String field, String v) {
        try {
            return LocalTime.parse(v);
        } catch (Exception e) {
            err.put(field, "Enter a valid time (HH:mm).");
            return null;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
