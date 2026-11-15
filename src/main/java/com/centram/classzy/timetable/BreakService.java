package com.centram.classzy.timetable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.timetable.TimetableDto.BreakRuleBody;
import com.centram.classzy.timetable.TimetableDto.BreakRuleView;
import com.centram.classzy.timetable.TimetableDto.BreakTypeBody;
import com.centram.classzy.timetable.TimetableDto.BreakTypeView;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Break Rules: break types and break rules. */
@Service
@Transactional
public class BreakService {

    private static final Set<String> APPLICABLE = Set.of("TEACHING_PERIODS", "EXAMS", "EVENTS", "TRANSPORT", "SPECIAL_DAYS", "OTHER");
    private static final Set<String> APPLIES_TO = Set.of("ALL_CLASSES", "GRADE_RANGE");
    private static final Set<String> FREQUENCY = Set.of("DAILY", "SELECTED_DAYS");

    private final CurrentUser current;
    private final TimetableBootstrap bootstrap;
    private final BreakTypeRepository types;
    private final BreakRuleRepository rules;
    private final AuditService audit;

    public BreakService(CurrentUser current, TimetableBootstrap bootstrap, BreakTypeRepository types, BreakRuleRepository rules, AuditService audit) {
        this.current = current;
        this.bootstrap = bootstrap;
        this.types = types;
        this.rules = rules;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ break types

    public List<BreakTypeView> listTypes() {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        return types.findByInstitutionIdOrderByIdAsc(inst).stream().map(BreakTypeView::of).toList();
    }

    public BreakTypeView createType(BreakTypeBody b) {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        BreakType t = new BreakType();
        t.setInstitutionId(inst);
        applyType(t, b, inst, 0L);
        t = types.saveAndFlush(t);
        audit.record("BREAK_TYPE_CREATED", "BreakType", t.getId(), inst, "Break type " + t.name + " created");
        return BreakTypeView.of(t);
    }

    public BreakTypeView updateType(Long id, BreakTypeBody b) {
        Long inst = current.institutionId();
        BreakType t = requireType(id, inst);
        applyType(t, b, inst, id);
        t = types.saveAndFlush(t);
        audit.record("BREAK_TYPE_UPDATED", "BreakType", id, inst, "Break type " + t.name + " updated");
        return BreakTypeView.of(t);
    }

    public void deleteType(Long id) {
        Long inst = current.institutionId();
        BreakType t = requireType(id, inst);
        if (rules.existsByInstitutionIdAndBreakTypeId(inst, id)) throw ApiException.conflict("This break type is used by a break rule. Change or delete the rule first.");
        types.delete(t);
        audit.record("BREAK_TYPE_DELETED", "BreakType", id, inst, "Break type " + t.name + " deleted");
    }

    private BreakType requireType(Long id, Long inst) {
        return types.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Break type"));
    }

    private void applyType(BreakType t, BreakTypeBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        String code = trim(b.code());
        if (name.isEmpty()) err.put("name", "Break type name is required.");
        else if (name.length() > 60) err.put("name", "Name is too long.");
        else if (types.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A break type with this name already exists.");
        if (code.isEmpty()) err.put("code", "Code is required.");
        else if (code.length() > 10) err.put("code", "Code can be at most 10 characters.");
        else if (types.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(inst, code, self)) err.put("code", "A break type with this code already exists.");
        if (b.defaultDuration() < 1 || b.defaultDuration() > 180) err.put("defaultDuration", "Duration must be between 1 and 180 minutes.");
        if (b.color() == null || !b.color().matches("#[0-9A-Fa-f]{6}")) err.put("color", "Colour must look like #4CAF50.");
        List<String> applicable = b.applicableFor() == null ? List.of() : b.applicableFor();
        if (applicable.isEmpty() || !APPLICABLE.containsAll(applicable)) err.put("applicableFor", "Choose at least one option.");
        if (b.description() != null && b.description().length() > 300) err.put("description", "Description can be at most 300 characters.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        t.name = name;
        t.code = code.toUpperCase();
        t.defaultDuration = b.defaultDuration();
        t.color = b.color().toUpperCase();
        t.applicableFor = String.join(",", new LinkedHashSet<>(applicable));
        t.active = b.active();
        t.description = b.description() == null || b.description().isBlank() ? null : b.description().trim();
    }

    // ------------------------------------------------------------------ break rules

    public List<BreakRuleView> listRules() {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        Map<Long, String> names = types.findByInstitutionIdOrderByIdAsc(inst).stream().collect(Collectors.toMap(BreakType::getId, x -> x.name, (a, c) -> a));
        return rules.findByInstitutionIdOrderByStartTimeAscIdAsc(inst).stream().map(r -> BreakRuleView.of(r, names.get(r.breakTypeId))).toList();
    }

    public BreakRuleView createRule(BreakRuleBody b) {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        BreakRule r = new BreakRule();
        r.setInstitutionId(inst);
        String typeName = applyRule(r, b, inst, 0L);
        r = rules.saveAndFlush(r);
        audit.record("BREAK_RULE_CREATED", "BreakRule", r.getId(), inst, "Break rule " + r.name + " created");
        return BreakRuleView.of(r, typeName);
    }

    public BreakRuleView updateRule(Long id, BreakRuleBody b) {
        Long inst = current.institutionId();
        BreakRule r = rules.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Break rule"));
        String typeName = applyRule(r, b, inst, id);
        r = rules.saveAndFlush(r);
        audit.record("BREAK_RULE_UPDATED", "BreakRule", id, inst, "Break rule " + r.name + " updated");
        return BreakRuleView.of(r, typeName);
    }

    public void deleteRule(Long id) {
        Long inst = current.institutionId();
        BreakRule r = rules.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Break rule"));
        rules.delete(r);
        audit.record("BREAK_RULE_DELETED", "BreakRule", id, inst, "Break rule " + r.name + " deleted");
    }

    /** Validates and copies the body onto the entity; returns the break type's name. */
    private String applyRule(BreakRule r, BreakRuleBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Rule name is required.");
        else if (name.length() > 80) err.put("name", "Rule name is too long.");
        else if (rules.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A break rule with this name already exists.");
        BreakType type = b.breakTypeId() == null ? null : types.findByIdAndInstitutionId(b.breakTypeId(), inst).orElse(null);
        if (type == null) err.put("breakTypeId", "Choose a break type.");
        if (!APPLIES_TO.contains(b.appliesTo())) err.put("appliesTo", "Choose who the rule applies to.");
        Integer from = b.classFrom();
        Integer to = b.classTo();
        if ("GRADE_RANGE".equals(b.appliesTo())) {
            if (from == null || to == null) err.put("classFrom", "Choose the first and last grade.");
            else if (from < 0 || to > 12 || from > to) err.put("classFrom", "Grades must be a valid range within 0-12.");
        } else {
            from = null;
            to = null;
        }
        LocalTime start = null;
        try {
            start = LocalTime.parse(b.startTime());
        } catch (Exception e) {
            err.put("startTime", "Enter a valid time (HH:mm).");
        }
        if (b.durationMinutes() < 1 || b.durationMinutes() > 180) err.put("durationMinutes", "Duration must be between 1 and 180 minutes.");
        if (!FREQUENCY.contains(b.frequency())) err.put("frequency", "Choose how often.");
        List<String> days = b.days() == null ? List.of() : b.days();
        try {
            for (String d : days) DayOfWeek.valueOf(d);
        } catch (IllegalArgumentException e) {
            err.put("days", "Invalid day.");
        }
        if ("SELECTED_DAYS".equals(b.frequency()) && days.isEmpty()) err.putIfAbsent("days", "Choose at least one day.");
        if (b.description() != null && b.description().length() > 500) err.put("description", "Description can be at most 500 characters.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        r.name = name;
        r.breakTypeId = type.getId();
        r.appliesTo = b.appliesTo();
        r.classFrom = from;
        r.classTo = to;
        r.startTime = start;
        r.durationMinutes = b.durationMinutes();
        r.frequency = b.frequency();
        r.days = "SELECTED_DAYS".equals(b.frequency()) ? String.join(",", new LinkedHashSet<>(days)) : null;
        r.active = b.active();
        r.description = b.description() == null || b.description().isBlank() ? null : b.description().trim();
        return type.name;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

}
