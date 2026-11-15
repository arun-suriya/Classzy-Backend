package com.centram.classzy.timetable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.timetable.TimetableDto.PriorityBody;
import com.centram.classzy.timetable.TimetableDto.PriorityView;
import com.centram.classzy.timetable.TimetableDto.Rules;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Timetable Rules: general rules, rule priorities and conflict handling. */
@Service
@Transactional
public class RulesService {

    private static final Set<String> CONFLICT_ITEMS = Set.of("TEACHER_AVAILABILITY", "CLASS_SUBJECT_REQUIREMENTS", "ROOM_AVAILABILITY",
            "SUBJECT_SEQUENCE", "PREFERRED_SLOTS", "EQUAL_DISTRIBUTION");
    private static final Set<String> TEACHER_ACTIONS = Set.of("SUGGEST_ALTERNATIVE", "SKIP", "WARN");
    private static final Set<String> ROOM_ACTIONS = Set.of("SUGGEST_ALTERNATIVE", "SKIP", "WARN");
    private static final Set<String> SUBJECT_ACTIONS = Set.of("NEXT_SLOT", "SKIP", "WARN");
    private static final Set<String> LIMIT_ACTIONS = Set.of("WARN_APPROVAL", "BLOCK", "ALLOW");

    private final CurrentUser current;
    private final TimetableBootstrap bootstrap;
    private final TimetableRuleSettingsRepository repo;
    private final RulePriorityRepository priorities;
    private final AuditService audit;

    public RulesService(CurrentUser current, TimetableBootstrap bootstrap, TimetableRuleSettingsRepository repo,
                        RulePriorityRepository priorities, AuditService audit) {
        this.current = current;
        this.bootstrap = bootstrap;
        this.repo = repo;
        this.priorities = priorities;
        this.audit = audit;
    }

    public Rules get() {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        return toDto(repo.findById(inst).orElseThrow());
    }

    public Rules update(Rules r) {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        TimetableRuleSettings e = repo.findById(inst).orElseThrow();
        Map<String, String> err = new LinkedHashMap<>();
        range(err, "maxPeriodsPerDay", r.maxPeriodsPerDay(), 1, 12);
        range(err, "maxConsecutiveTeacher", r.maxConsecutiveTeacher(), 1, 12);
        range(err, "minBreakBetween", r.minBreakBetween(), 0, 120);
        range(err, "maxConsecutiveSubject", r.maxConsecutiveSubject(), 1, 8);
        if (r.rulesDescription() != null && r.rulesDescription().length() > 500) err.put("rulesDescription", "Description can be at most 500 characters.");
        if (!TEACHER_ACTIONS.contains(r.onTeacherConflict())) err.put("onTeacherConflict", "Choose an option.");
        if (!ROOM_ACTIONS.contains(r.onRoomConflict())) err.put("onRoomConflict", "Choose an option.");
        if (!SUBJECT_ACTIONS.contains(r.onSubjectConflict())) err.put("onSubjectConflict", "Choose an option.");
        if (!LIMIT_ACTIONS.contains(r.onLimitExceeded())) err.put("onLimitExceeded", "Choose an option.");
        List<String> order = r.conflictOrder() == null ? List.of() : r.conflictOrder();
        if (order.size() != CONFLICT_ITEMS.size() || !new LinkedHashSet<>(order).equals(CONFLICT_ITEMS)) err.put("conflictOrder", "The priority list must contain each item exactly once.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        e.maxPeriodsPerDay = r.maxPeriodsPerDay();
        e.maxConsecutiveTeacher = r.maxConsecutiveTeacher();
        e.minBreakBetween = r.minBreakBetween();
        e.maxConsecutiveSubject = r.maxConsecutiveSubject();
        e.allowDoublePeriods = r.allowDoublePeriods();
        e.allowFreePeriods = r.allowFreePeriods();
        e.preferMorningCore = r.preferMorningCore();
        e.genAvoidTeacherConflicts = r.genAvoidTeacherConflicts();
        e.genAvoidRoomConflicts = r.genAvoidRoomConflicts();
        e.genDistributeEvenly = r.genDistributeEvenly();
        e.genWeeklyFrequency = r.genWeeklyFrequency();
        e.genWorkingHours = r.genWorkingHours();
        e.genPreferredSlots = r.genPreferredSlots();
        e.genManualAdjust = r.genManualAdjust();
        e.genAvoidConsecutive = r.genAvoidConsecutive();
        e.genDailyDiversity = r.genDailyDiversity();
        e.genTeacherWorkload = r.genTeacherWorkload();
        e.genAiSuggestions = r.genAiSuggestions();
        e.rulesDescription = r.rulesDescription() == null || r.rulesDescription().isBlank() ? null : r.rulesDescription().trim();
        e.onTeacherConflict = r.onTeacherConflict();
        e.onRoomConflict = r.onRoomConflict();
        e.onSubjectConflict = r.onSubjectConflict();
        e.onLimitExceeded = r.onLimitExceeded();
        e.conflictOrder = String.join(",", order);
        e.advAutoResolve = r.advAutoResolve();
        e.advReport = r.advReport();
        e.advHighlight = r.advHighlight();
        e.advManualOverride = r.advManualOverride();
        repo.save(e);
        audit.record("TIMETABLE_RULES_UPDATED", "TimetableRuleSettings", inst, inst, "Timetable rules updated");
        return toDto(e);
    }

    // ------------------------------------------------------------------ priorities

    public List<PriorityView> listPriorities() {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        return priorities.findByInstitutionIdOrderByPositionAsc(inst).stream().map(PriorityView::of).toList();
    }

    /** Saves the order (position in the list) and on/off state of all rule priorities. */
    public List<PriorityView> savePriorities(List<PriorityBody> body) {
        Long inst = current.institutionId();
        bootstrap.ensure(inst);
        List<RulePriority> existing = priorities.findByInstitutionIdOrderByPositionAsc(inst);
        Map<String, RulePriority> byCode = new LinkedHashMap<>();
        existing.forEach(p -> byCode.put(p.code, p));
        if (body == null || body.size() != existing.size() || !byCode.keySet().equals(body.stream().map(PriorityBody::code).collect(java.util.stream.Collectors.toSet()))) {
            throw ApiException.badRequest("Send every rule priority exactly once.");
        }
        int pos = 1;
        for (PriorityBody b : body) {
            RulePriority p = byCode.get(b.code());
            p.position = pos++;
            p.enabled = b.enabled();
        }
        priorities.saveAll(byCode.values());
        audit.record("RULE_PRIORITIES_UPDATED", "TimetableRulePriority", inst, inst, "Rule priorities updated");
        return priorities.findByInstitutionIdOrderByPositionAsc(inst).stream().map(PriorityView::of).toList();
    }

    private Rules toDto(TimetableRuleSettings e) {
        return new Rules(e.maxPeriodsPerDay, e.maxConsecutiveTeacher, e.minBreakBetween, e.maxConsecutiveSubject,
                e.allowDoublePeriods, e.allowFreePeriods, e.preferMorningCore, e.genAvoidTeacherConflicts, e.genAvoidRoomConflicts,
                e.genDistributeEvenly, e.genWeeklyFrequency, e.genWorkingHours, e.genPreferredSlots, e.genManualAdjust,
                e.genAvoidConsecutive, e.genDailyDiversity, e.genTeacherWorkload, e.genAiSuggestions, e.rulesDescription,
                e.onTeacherConflict, e.onRoomConflict, e.onSubjectConflict, e.onLimitExceeded, List.of(e.conflictOrder.split(",")),
                e.advAutoResolve, e.advReport, e.advHighlight, e.advManualOverride);
    }

    private static void range(Map<String, String> err, String field, int v, int min, int max) {
        if (v < min || v > max) err.put(field, "Enter a value between " + min + " and " + max + ".");
    }
}
