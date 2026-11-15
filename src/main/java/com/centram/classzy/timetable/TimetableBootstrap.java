package com.centram.classzy.timetable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the settings rows (and starter break types / rule priorities) the first time an institution opens Timetable Settings. */
@Service
@Transactional
public class TimetableBootstrap {

    private final TimetableSettingsRepository settings;
    private final TimetableRuleSettingsRepository ruleSettings;
    private final RulePriorityRepository priorities;
    private final BreakTypeRepository breakTypes;

    public TimetableBootstrap(TimetableSettingsRepository settings, TimetableRuleSettingsRepository ruleSettings,
                              RulePriorityRepository priorities, BreakTypeRepository breakTypes) {
        this.settings = settings;
        this.ruleSettings = ruleSettings;
        this.priorities = priorities;
        this.breakTypes = breakTypes;
    }

    /** Safe to call on every request. Only the very first call (per institution) seeds the defaults. */
    public void ensure(Long inst) {
        if (settings.createIfMissing(inst) == 1) {
            ruleSettings.createIfMissing(inst);
            String[][] rules = {
                    {"TEACHER_CONFLICTS", "Avoid teacher conflicts"}, {"ROOM_CONFLICTS", "Avoid room conflicts"},
                    {"SUBJECT_FREQUENCY", "Maintain subject frequency"}, {"EVEN_DISTRIBUTION", "Distribute periods evenly"},
                    {"PREFERRED_SLOTS", "Preferred time slots"}, {"LIMIT_CONSECUTIVE", "Limit consecutive periods"}};
            int pos = 1;
            for (String[] r : rules) {
                RulePriority p = new RulePriority();
                p.setInstitutionId(inst);
                p.code = r[0];
                p.name = r[1];
                p.position = pos++;
                priorities.save(p);
            }
            Object[][] types = {
                    {"Short Break", "SB", 15, "#4CAF50", "TEACHING_PERIODS", "Short break between periods."},
                    {"Lunch Break", "LB", 45, "#FF9800", "TEACHING_PERIODS", "Main lunch break for regular school days."},
                    {"Long Break", "LG", 60, "#2196F3", "SPECIAL_DAYS", "Extended break for special days."},
                    {"Special Break", "SP", 20, "#9C2780", "EVENTS,EXAMS", "Break used during events and exams."},
                    {"Assembly Break", "AB", 30, "#795548", "OTHER", "Morning assembly."},
                    {"Transport Break", "TB", 10, "#607D8B", "TRANSPORT", "Buffer for transport arrival and departure."}};
            for (Object[] t : types) {
                BreakType b = new BreakType();
                b.setInstitutionId(inst);
                b.name = (String) t[0];
                b.code = (String) t[1];
                b.defaultDuration = (Integer) t[2];
                b.color = (String) t[3];
                b.applicableFor = (String) t[4];
                b.description = (String) t[5];
                breakTypes.save(b);
            }
        }
    }
}
