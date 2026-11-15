package com.centram.classzy.attendance;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.centram.classzy.attendance.AttendanceDto.EventBody;
import com.centram.classzy.attendance.AttendanceDto.EventView;
import com.centram.classzy.attendance.AttendanceDto.RuleBody;
import com.centram.classzy.attendance.AttendanceDto.RuleView;
import com.centram.classzy.attendance.AttendanceDto.Settings;
import com.centram.classzy.attendance.AttendanceDto.TemplateBody;
import com.centram.classzy.attendance.AttendanceDto.TemplateView;
import com.centram.classzy.attendance.AttendanceDto.TypeBody;
import com.centram.classzy.attendance.AttendanceDto.TypeView;
import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.institution.Institution;
import com.centram.classzy.institution.InstitutionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AttendanceService {

    private static final Set<String> MODES = Set.of("DAILY", "PERIOD_WISE", "BOTH");
    private static final Set<String> METHODS = Set.of("MANUAL", "BIOMETRIC", "RFID", "MOBILE_APP");
    private static final Set<String> MARKS = Set.of("PRESENT", "ABSENT");
    private static final Set<String> CATEGORIES = Set.of("REGULAR", "LEAVE", "NON_WORKING", "SPECIAL");
    private static final Set<String> COUNTS_AS = Set.of("PRESENT", "ABSENT", "PRESENT_LATE", "PRESENT_HALF", "LEAVE", "NON_WORKING", "EXCUSED");
    private static final Set<String> APPLIES_TO = Set.of("ALL_STUDENTS", "ALL_STAFF", "ALL");
    private static final Set<String> FIELDS = Set.of("LATE_MINUTES", "WORKED_MINUTES", "ABSENT_DAYS_IN_ROW", "ATTENDANCE_PCT");
    private static final Set<String> OPERATORS = Set.of("GREATER_THAN", "GREATER_OR_EQUAL", "LESS_THAN", "LESS_OR_EQUAL", "EQUALS");
    private static final Set<String> TRIGGERS = Set.of("ABSENT_AT_DAY_END", "LATE_CHECK_IN", "HALF_DAY", "DAY_END_SUMMARY", "LOW_ATTENDANCE");
    private static final Set<String> RECIPIENTS = Set.of("PARENT", "CLASS_TEACHER", "PRINCIPAL", "ADMIN");
    private static final Set<String> CHANNELS = Set.of("EMAIL", "SMS", "APP");

    private final CurrentUser current;
    private final AttendanceSettingsRepository settingsRepo;
    private final AttendanceTypeRepository types;
    private final AttendanceRuleRepository rules;
    private final AttendanceTemplateRepository templates;
    private final AttendanceEventRepository events;
    private final InstitutionRepository institutions;
    private final AuditService audit;

    public AttendanceService(CurrentUser current, AttendanceSettingsRepository settingsRepo,
                             AttendanceTypeRepository types, AttendanceRuleRepository rules,
                             AttendanceTemplateRepository templates, AttendanceEventRepository events,
                             InstitutionRepository institutions, AuditService audit) {
        this.current = current;
        this.settingsRepo = settingsRepo;
        this.types = types;
        this.rules = rules;
        this.templates = templates;
        this.events = events;
        this.institutions = institutions;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ settings

    /** Returns the institution's settings, creating them (with defaults for every list) on first use. */
    private AttendanceSettings ensure(Long inst) {
        if (settingsRepo.createIfMissing(inst) == 1) {
            seed(inst);
        }
        return settingsRepo.findById(inst).orElseThrow(() -> ApiException.notFound("Attendance settings"));
    }

    @Transactional
    public Settings getSettings() {
        Long inst = current.institutionId();
        return toSettings(ensure(inst), institution(inst));
    }

    public Settings updateSettings(Settings s) {
        Long inst = current.institutionId();
        AttendanceSettings a = ensure(inst);
        Institution i = institution(inst);
        Map<String, String> err = new LinkedHashMap<>();
        if (!MODES.contains(s.attendanceMode())) err.put("attendanceMode", "Choose a valid attendance mode.");
        if (!METHODS.contains(s.defaultMethod())) err.put("defaultMethod", "Choose a valid marking method.");
        if (!MARKS.contains(s.defaultMark())) err.put("defaultMark", "Choose Present or Absent.");
        range(err, "lateAfterMinutes", s.lateAfterMinutes(), 0, 600);
        range(err, "halfDayMinutes", s.halfDayMinutes(), 0, 1440);
        range(err, "minAttendancePct", s.minAttendancePct(), 0, 100);
        range(err, "gracePeriodMinutes", s.gracePeriodMinutes(), 0, 120);
        LocalTime autoAbsent = time(err, "autoAbsentTime", s.autoAbsentTime());
        LocalTime start = time(err, "startTime", s.startTime());
        LocalTime end = time(err, "endTime", s.endTime());
        if (start != null && end != null && !end.isAfter(start)) err.put("endTime", "End time must be after start time.");
        Set<DayOfWeek> days = new java.util.TreeSet<>();
        try {
            for (String d : s.workingDays() == null ? List.<String>of() : s.workingDays()) days.add(DayOfWeek.valueOf(d));
        } catch (IllegalArgumentException e) {
            err.put("workingDays", "Invalid working day.");
        }
        if (days.isEmpty()) err.put("workingDays", "Select at least one working day.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        a.attendanceMode = s.attendanceMode();
        a.defaultMethod = s.defaultMethod();
        a.defaultMark = s.defaultMark();
        a.lateAfterMinutes = s.lateAfterMinutes();
        a.halfDayMinutes = s.halfDayMinutes();
        a.biometric = s.biometric();
        a.rfid = s.rfid();
        a.mobileApp = s.mobileApp();
        a.gps = s.gps();
        a.offlineMode = s.offlineMode();
        a.autoSync = s.autoSync();
        a.parentView = s.parentView();
        a.studentView = s.studentView();
        a.remarksEnabled = s.remarksEnabled();
        a.adjustmentEnabled = s.adjustmentEnabled();
        a.minAttendancePct = s.minAttendancePct();
        a.countPresent = s.countPresent();
        a.countLate = s.countLate();
        a.countHalfDay = s.countHalfDay();
        a.countOnLeave = s.countOnLeave();
        a.countSickLeave = s.countSickLeave();
        a.countExcused = s.countExcused();
        a.excludeHolidays = s.excludeHolidays();
        a.excludeWeekOffs = s.excludeWeekOffs();
        a.gracePeriodMinutes = s.gracePeriodMinutes();
        a.autoAbsentTime = autoAbsent;
        settingsRepo.save(a);

        i.getWorkingDays().clear();
        i.getWorkingDays().addAll(days);
        i.setStartTime(start);
        i.setEndTime(end);
        i.touch();
        institutions.save(i);
        audit.record("ATTENDANCE_SETTINGS_UPDATED", "AttendanceSettings", inst, inst, "Attendance settings updated");
        return toSettings(a, i);
    }

    private Settings toSettings(AttendanceSettings a, Institution i) {
        return new Settings(a.attendanceMode, a.defaultMethod, a.defaultMark, a.lateAfterMinutes, a.halfDayMinutes,
                a.biometric, a.rfid, a.mobileApp, a.gps, a.offlineMode, a.autoSync,
                a.parentView, a.studentView, a.remarksEnabled, a.adjustmentEnabled,
                a.minAttendancePct, a.countPresent, a.countLate, a.countHalfDay, a.countOnLeave, a.countSickLeave, a.countExcused,
                a.excludeHolidays, a.excludeWeekOffs, a.gracePeriodMinutes, a.autoAbsentTime.toString().substring(0, 5),
                i.getWorkingDays().stream().sorted().map(Enum::name).toList(),
                i.getStartTime() == null ? null : i.getStartTime().toString().substring(0, 5),
                i.getEndTime() == null ? null : i.getEndTime().toString().substring(0, 5));
    }

    // ------------------------------------------------------------------ types

    @Transactional
    public List<TypeView> listTypes() {
        Long inst = current.institutionId();
        ensure(inst);
        return types.findByInstitutionIdOrderBySortOrderAscIdAsc(inst).stream().map(TypeView::of).toList();
    }

    public TypeView createType(TypeBody b) {
        Long inst = current.institutionId();
        ensure(inst);
        validateType(inst, null, b);
        if (b.defaultType()) types.clearDefault(inst, 0L);
        AttendanceType t = new AttendanceType();
        t.setInstitutionId(inst);
        applyType(t, b);
        t.sortOrder = (int) types.count() + 100;
        t = types.saveAndFlush(t);
        audit.record("ATTENDANCE_TYPE_CREATED", "AttendanceType", t.getId(), inst, "Attendance type " + t.name + " created");
        return TypeView.of(t);
    }

    public TypeView updateType(Long id, TypeBody b) {
        Long inst = current.institutionId();
        AttendanceType existing = requireType(id, inst);
        validateType(inst, id, b);
        if (existing.defaultType && !b.defaultType()) {
            throw ApiException.badRequest("Choose another attendance type as the default instead of unsetting this one.");
        }
        if (!existing.code.equalsIgnoreCase(b.code().trim()) && rules.existsByInstitutionIdAndActionTypeCodeIgnoreCase(inst, existing.code)) {
            throw ApiException.conflict("This type's code is used by an attendance rule, so it can't be changed.");
        }
        if (b.defaultType() && !existing.defaultType) types.clearDefault(inst, id);
        AttendanceType t = requireType(id, inst);
        applyType(t, b);
        t = types.saveAndFlush(t);
        audit.record("ATTENDANCE_TYPE_UPDATED", "AttendanceType", id, inst, "Attendance type " + t.name + " updated");
        return TypeView.of(t);
    }

    public void deleteType(Long id) {
        Long inst = current.institutionId();
        AttendanceType t = requireType(id, inst);
        if (t.defaultType) throw ApiException.conflict("The default attendance type can't be deleted. Make another type the default first.");
        if (rules.existsByInstitutionIdAndActionTypeCodeIgnoreCase(inst, t.code)) {
            throw ApiException.conflict("This type is used by an attendance rule. Change or delete the rule first.");
        }
        types.delete(t);
        audit.record("ATTENDANCE_TYPE_DELETED", "AttendanceType", id, inst, "Attendance type " + t.name + " deleted");
    }

    private void validateType(Long inst, Long id, TypeBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        long self = id == null ? 0L : id;
        String name = trim(b.name());
        String code = trim(b.code());
        if (name.isEmpty()) err.put("name", "Name is required.");
        else if (name.length() > 60) err.put("name", "Name is too long.");
        else if (types.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A type with this name already exists.");
        if (code.isEmpty()) err.put("code", "Code is required.");
        else if (code.length() > 10) err.put("code", "Code can be at most 10 characters.");
        else if (types.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(inst, code, self)) err.put("code", "A type with this code already exists.");
        if (!CATEGORIES.contains(b.category())) err.put("category", "Choose a category.");
        if (!COUNTS_AS.contains(b.countsAs())) err.put("countsAs", "Choose how this type is counted.");
        if (b.color() == null || !b.color().matches("#[0-9A-Fa-f]{6}")) err.put("color", "Colour must look like #28A745.");
        if (b.defaultType() && !b.active()) err.put("active", "The default type must be active.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
    }

    private void applyType(AttendanceType t, TypeBody b) {
        t.name = trim(b.name());
        t.code = trim(b.code()).toUpperCase();
        t.category = b.category();
        t.countsAs = b.countsAs();
        t.color = b.color().toUpperCase();
        t.defaultType = b.defaultType();
        t.active = b.active();
        t.description = blankToNull(b.description());
    }

    private AttendanceType requireType(Long id, Long inst) {
        return types.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Attendance type"));
    }

    // ------------------------------------------------------------------ rules

    @Transactional
    public List<RuleView> listRules() {
        Long inst = current.institutionId();
        ensure(inst);
        return rules.findByInstitutionIdOrderByPriorityAscIdAsc(inst).stream().map(RuleView::of).toList();
    }

    public RuleView createRule(RuleBody b) {
        Long inst = current.institutionId();
        ensure(inst);
        validateRule(inst, 0L, b);
        AttendanceRule r = new AttendanceRule();
        r.setInstitutionId(inst);
        applyRule(r, b);
        r = rules.saveAndFlush(r);
        audit.record("ATTENDANCE_RULE_CREATED", "AttendanceRule", r.getId(), inst, "Attendance rule " + r.name + " created");
        return RuleView.of(r);
    }

    public RuleView updateRule(Long id, RuleBody b) {
        Long inst = current.institutionId();
        AttendanceRule r = rules.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Attendance rule"));
        validateRule(inst, id, b);
        applyRule(r, b);
        r = rules.saveAndFlush(r);
        audit.record("ATTENDANCE_RULE_UPDATED", "AttendanceRule", id, inst, "Attendance rule " + r.name + " updated");
        return RuleView.of(r);
    }

    public void deleteRule(Long id) {
        Long inst = current.institutionId();
        AttendanceRule r = rules.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Attendance rule"));
        rules.delete(r);
        audit.record("ATTENDANCE_RULE_DELETED", "AttendanceRule", id, inst, "Attendance rule " + r.name + " deleted");
    }

    private void validateRule(Long inst, long self, RuleBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Rule name is required.");
        else if (name.length() > 80) err.put("name", "Rule name is too long.");
        else if (rules.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A rule with this name already exists.");
        if (!APPLIES_TO.contains(b.appliesTo())) err.put("appliesTo", "Choose who the rule applies to.");
        if (b.priority() < 1 || b.priority() > 99) err.put("priority", "Priority must be between 1 and 99.");
        if (!FIELDS.contains(b.conditionField())) err.put("conditionField", "Choose a condition field.");
        if (!OPERATORS.contains(b.conditionOperator())) err.put("conditionOperator", "Choose an operator.");
        String value = trim(b.conditionValue());
        if (!value.matches("\\d{1,6}")) err.put("conditionValue", "Enter a whole number.");
        String code = trim(b.actionTypeCode());
        if (code.isEmpty() || types.findByInstitutionIdAndCodeIgnoreCase(inst, code).isEmpty()) {
            err.put("actionTypeCode", "Choose an existing attendance type.");
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
    }

    private void applyRule(AttendanceRule r, RuleBody b) {
        r.name = trim(b.name());
        r.description = blankToNull(b.description());
        r.appliesTo = b.appliesTo();
        r.priority = b.priority();
        r.active = b.active();
        r.conditionField = b.conditionField();
        r.conditionOperator = b.conditionOperator();
        r.conditionValue = trim(b.conditionValue());
        r.actionTypeCode = trim(b.actionTypeCode()).toUpperCase();
        r.notifyParent = b.notifyParent();
        r.includeInReports = b.includeInReports();
    }

    // ------------------------------------------------------------------ templates

    @Transactional
    public List<TemplateView> listTemplates() {
        Long inst = current.institutionId();
        ensure(inst);
        return templates.findByInstitutionIdOrderByIdAsc(inst).stream().map(TemplateView::of).toList();
    }

    public TemplateView createTemplate(TemplateBody b) {
        Long inst = current.institutionId();
        ensure(inst);
        validateTemplate(inst, 0L, b);
        AttendanceTemplate t = new AttendanceTemplate();
        t.setInstitutionId(inst);
        t.name = trim(b.name());
        t.body = trim(b.body());
        t.active = b.active();
        t = templates.saveAndFlush(t);
        audit.record("ATTENDANCE_TEMPLATE_CREATED", "AttendanceTemplate", t.getId(), inst, "Notification template " + t.name + " created");
        return TemplateView.of(t);
    }

    public TemplateView updateTemplate(Long id, TemplateBody b) {
        Long inst = current.institutionId();
        AttendanceTemplate t = templates.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Template"));
        validateTemplate(inst, id, b);
        t.name = trim(b.name());
        t.body = trim(b.body());
        t.active = b.active();
        t = templates.saveAndFlush(t);
        audit.record("ATTENDANCE_TEMPLATE_UPDATED", "AttendanceTemplate", id, inst, "Notification template " + t.name + " updated");
        return TemplateView.of(t);
    }

    public void deleteTemplate(Long id) {
        Long inst = current.institutionId();
        AttendanceTemplate t = templates.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Template"));
        if (events.existsByInstitutionIdAndTemplateId(inst, id)) {
            throw ApiException.conflict("This template is used by a notification event. Change that event first.");
        }
        templates.delete(t);
        audit.record("ATTENDANCE_TEMPLATE_DELETED", "AttendanceTemplate", id, inst, "Notification template " + t.name + " deleted");
    }

    private void validateTemplate(Long inst, long self, TemplateBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        String body = trim(b.body());
        if (name.isEmpty()) err.put("name", "Template name is required.");
        else if (name.length() > 80) err.put("name", "Template name is too long.");
        else if (templates.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A template with this name already exists.");
        if (body.isEmpty()) err.put("body", "Message is required.");
        else if (body.length() > 1000) err.put("body", "Message can be at most 1000 characters.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
    }

    // ------------------------------------------------------------------ notification events

    @Transactional
    public List<EventView> listEvents() {
        Long inst = current.institutionId();
        ensure(inst);
        return events.findByInstitutionIdOrderByIdAsc(inst).stream().map(EventView::of).toList();
    }

    public EventView createEvent(EventBody b) {
        Long inst = current.institutionId();
        ensure(inst);
        validateEvent(inst, 0L, b);
        AttendanceNotificationEvent e = new AttendanceNotificationEvent();
        e.setInstitutionId(inst);
        applyEvent(e, b);
        e = events.saveAndFlush(e);
        audit.record("ATTENDANCE_EVENT_CREATED", "AttendanceNotificationEvent", e.getId(), inst, "Notification event " + e.name + " created");
        return EventView.of(e);
    }

    public EventView updateEvent(Long id, EventBody b) {
        Long inst = current.institutionId();
        AttendanceNotificationEvent e = events.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Notification event"));
        validateEvent(inst, id, b);
        applyEvent(e, b);
        e = events.saveAndFlush(e);
        audit.record("ATTENDANCE_EVENT_UPDATED", "AttendanceNotificationEvent", id, inst, "Notification event " + e.name + " updated");
        return EventView.of(e);
    }

    public void deleteEvent(Long id) {
        Long inst = current.institutionId();
        AttendanceNotificationEvent e = events.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Notification event"));
        events.delete(e);
        audit.record("ATTENDANCE_EVENT_DELETED", "AttendanceNotificationEvent", id, inst, "Notification event " + e.name + " deleted");
    }

    private void validateEvent(Long inst, long self, EventBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Event name is required.");
        else if (name.length() > 80) err.put("name", "Event name is too long.");
        else if (events.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "An event with this name already exists.");
        if (!TRIGGERS.contains(b.triggerCode())) err.put("triggerCode", "Choose when this notification is sent.");
        if (b.recipients() == null || b.recipients().isEmpty() || !RECIPIENTS.containsAll(b.recipients())) err.put("recipients", "Choose at least one recipient.");
        if (b.channels() == null || b.channels().isEmpty() || !CHANNELS.containsAll(b.channels())) err.put("channels", "Choose at least one channel.");
        if (b.templateId() != null && templates.findByIdAndInstitutionId(b.templateId(), inst).isEmpty()) err.put("templateId", "Template not found.");
        if (b.templateId() == null && trim(b.customMessage()).isEmpty()) err.put("templateId", "Choose a template or write a custom message.");
        if (b.customMessage() != null && b.customMessage().length() > 1000) err.put("customMessage", "Message can be at most 1000 characters.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
    }

    private void applyEvent(AttendanceNotificationEvent e, EventBody b) {
        e.name = trim(b.name());
        e.triggerCode = b.triggerCode();
        e.recipients = b.recipients().stream().distinct().collect(Collectors.joining(","));
        e.channels = b.channels().stream().distinct().collect(Collectors.joining(","));
        e.templateId = b.templateId();
        e.customMessage = blankToNull(b.customMessage());
        e.active = b.active();
    }

    // ------------------------------------------------------------------ defaults

    private void seed(Long inst) {
        String[][] t = {
                {"Present", "P", "REGULAR", "PRESENT", "#28A745", "Student is present for the day."},
                {"Absent", "A", "REGULAR", "ABSENT", "#DC3545", "Student is absent for the day."},
                {"Late", "L", "REGULAR", "PRESENT_LATE", "#FFC107", "Student arrived after the allowed time."},
                {"Half Day", "HD", "REGULAR", "PRESENT_HALF", "#FD7E14", "Student attended only part of the day."},
                {"On Leave", "LVE", "LEAVE", "LEAVE", "#17A2B8", "Approved leave."},
                {"Sick Leave", "SL", "LEAVE", "LEAVE", "#6F42C1", "Leave because of illness."},
                {"Permission", "PER", "LEAVE", "EXCUSED", "#20C997", "Short permission granted."},
                {"Holiday", "HOL", "NON_WORKING", "NON_WORKING", "#6C757D", "Public or institution holiday."},
                {"Week Off", "WO", "NON_WORKING", "NON_WORKING", "#ADB5BD", "Weekly off day."},
                {"Excused", "EX", "SPECIAL", "EXCUSED", "#0D6EFD", "Excused by the institution (events, sports, etc.)."},
        };
        int order = 1;
        for (String[] r : t) {
            AttendanceType x = new AttendanceType();
            x.setInstitutionId(inst);
            x.name = r[0]; x.code = r[1]; x.category = r[2]; x.countsAs = r[3]; x.color = r[4]; x.description = r[5];
            x.defaultType = "P".equals(r[1]);
            x.sortOrder = order++;
            types.save(x);
        }
        rule(inst, "Late Arrival", "Mark Late when the student arrives after the allowed time.", "LATE_MINUTES", "GREATER_THAN", "15", "L", true, 1);
        rule(inst, "Half Day", "Mark Half Day when the student stays less than 4 hours.", "WORKED_MINUTES", "LESS_THAN", "240", "HD", true, 2);
        rule(inst, "Auto Absent", "Mark Absent when the student is more than 2 hours late.", "LATE_MINUTES", "GREATER_THAN", "120", "A", true, 3);

        AttendanceTemplate absent = template(inst, "Absent Notice", "Dear Parent, {StudentName} of {ClassName} was marked absent on {Date}. - {InstitutionName}");
        AttendanceTemplate late = template(inst, "Late Arrival Notice", "Dear Parent, {StudentName} of {ClassName} arrived late on {Date}. - {InstitutionName}");
        AttendanceTemplate half = template(inst, "Half Day Notice", "Dear Parent, {StudentName} of {ClassName} was marked half day on {Date}. - {InstitutionName}");
        AttendanceTemplate low = template(inst, "Low Attendance Warning", "Dear Parent, {StudentName}'s attendance is {AttendancePercent}%, below the required minimum. - {InstitutionName}");
        event(inst, "Absent Alert", "ABSENT_AT_DAY_END", "PARENT", "SMS,APP", absent.getId());
        event(inst, "Late Arrival Alert", "LATE_CHECK_IN", "PARENT", "APP", late.getId());
        event(inst, "Half Day Alert", "HALF_DAY", "PARENT", "APP", half.getId());
        event(inst, "Daily Attendance Summary", "DAY_END_SUMMARY", "CLASS_TEACHER,PRINCIPAL", "EMAIL", null);
        event(inst, "Low Attendance Warning", "LOW_ATTENDANCE", "PARENT,CLASS_TEACHER", "EMAIL,SMS", low.getId());
    }

    private void rule(Long inst, String name, String desc, String field, String op, String value, String code, boolean notify, int priority) {
        AttendanceRule r = new AttendanceRule();
        r.setInstitutionId(inst);
        r.name = name; r.description = desc; r.conditionField = field; r.conditionOperator = op;
        r.conditionValue = value; r.actionTypeCode = code; r.notifyParent = notify; r.priority = priority;
        rules.save(r);
    }

    private AttendanceTemplate template(Long inst, String name, String body) {
        AttendanceTemplate t = new AttendanceTemplate();
        t.setInstitutionId(inst);
        t.name = name; t.body = body;
        return templates.saveAndFlush(t);
    }

    private void event(Long inst, String name, String trigger, String recipients, String channels, Long templateId) {
        AttendanceNotificationEvent e = new AttendanceNotificationEvent();
        e.setInstitutionId(inst);
        e.name = name; e.triggerCode = trigger; e.recipients = recipients; e.channels = channels; e.templateId = templateId;
        if (templateId == null) e.customMessage = "Attendance summary for {Date}: {PresentCount} present, {AbsentCount} absent.";
        events.save(e);
    }

    // ------------------------------------------------------------------ helpers

    private Institution institution(Long id) {
        return institutions.findById(id).orElseThrow(() -> ApiException.notFound("Institution"));
    }

    private static void range(Map<String, String> err, String field, int v, int min, int max) {
        if (v < min || v > max) err.put(field, "Enter a value between " + min + " and " + max + ".");
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
