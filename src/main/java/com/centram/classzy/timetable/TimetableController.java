package com.centram.classzy.timetable;

import java.util.List;

import com.centram.classzy.security.Roles;
import com.centram.classzy.timetable.TimetableDto.BreakRuleBody;
import com.centram.classzy.timetable.TimetableDto.BreakRuleView;
import com.centram.classzy.timetable.TimetableDto.BreakTypeBody;
import com.centram.classzy.timetable.TimetableDto.BreakTypeView;
import com.centram.classzy.timetable.TimetableDto.General;
import com.centram.classzy.timetable.TimetableDto.HolidayBody;
import com.centram.classzy.timetable.TimetableDto.HolidayView;
import com.centram.classzy.timetable.TimetableDto.PriorityBody;
import com.centram.classzy.timetable.TimetableDto.PriorityView;
import com.centram.classzy.timetable.TimetableDto.Rules;
import com.centram.classzy.timetable.TimetableDto.SlotBody;
import com.centram.classzy.timetable.TimetableDto.SlotView;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timetable")
@PreAuthorize(Roles.READ)
public class TimetableController {

    private final PeriodService periods;
    private final RulesService rules;
    private final BreakService breaks;

    public TimetableController(PeriodService periods, RulesService rules, BreakService breaks) {
        this.periods = periods;
        this.rules = rules;
        this.breaks = breaks;
    }

    // ---- period configuration
    @GetMapping("/settings")
    public General settings() { return periods.getGeneral(); }

    @PutMapping("/settings")
    @PreAuthorize(Roles.WRITE)
    public General saveSettings(@RequestBody General g) { return periods.updateGeneral(g); }

    @GetMapping("/slots")
    public List<SlotView> slots() { return periods.listSlots(); }

    @PostMapping("/slots")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public SlotView createSlot(@RequestBody SlotBody b) { return periods.createSlot(b); }

    @PutMapping("/slots/{id}")
    @PreAuthorize(Roles.WRITE)
    public SlotView updateSlot(@PathVariable Long id, @RequestBody SlotBody b) { return periods.updateSlot(id, b); }

    @DeleteMapping("/slots/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@PathVariable Long id) { periods.deleteSlot(id); }

    @GetMapping("/holidays")
    public List<HolidayView> holidays() { return periods.listHolidays(); }

    @PostMapping("/holidays")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayView createHoliday(@RequestBody HolidayBody b) { return periods.createHoliday(b); }

    @PutMapping("/holidays/{id}")
    @PreAuthorize(Roles.WRITE)
    public HolidayView updateHoliday(@PathVariable Long id, @RequestBody HolidayBody b) { return periods.updateHoliday(id, b); }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable Long id) { periods.deleteHoliday(id); }

    // ---- timetable rules
    @GetMapping("/rules")
    public Rules rules() { return rules.get(); }

    @PutMapping("/rules")
    @PreAuthorize(Roles.WRITE)
    public Rules saveRules(@RequestBody Rules r) { return rules.update(r); }

    @GetMapping("/rule-priorities")
    public List<PriorityView> priorities() { return rules.listPriorities(); }

    @PutMapping("/rule-priorities")
    @PreAuthorize(Roles.WRITE)
    public List<PriorityView> savePriorities(@RequestBody List<PriorityBody> body) { return rules.savePriorities(body); }

    // ---- break rules
    @GetMapping("/break-types")
    public List<BreakTypeView> breakTypes() { return breaks.listTypes(); }

    @PostMapping("/break-types")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public BreakTypeView createBreakType(@RequestBody BreakTypeBody b) { return breaks.createType(b); }

    @PutMapping("/break-types/{id}")
    @PreAuthorize(Roles.WRITE)
    public BreakTypeView updateBreakType(@PathVariable Long id, @RequestBody BreakTypeBody b) { return breaks.updateType(id, b); }

    @DeleteMapping("/break-types/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBreakType(@PathVariable Long id) { breaks.deleteType(id); }

    @GetMapping("/break-rules")
    public List<BreakRuleView> breakRules() { return breaks.listRules(); }

    @PostMapping("/break-rules")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public BreakRuleView createBreakRule(@RequestBody BreakRuleBody b) { return breaks.createRule(b); }

    @PutMapping("/break-rules/{id}")
    @PreAuthorize(Roles.WRITE)
    public BreakRuleView updateBreakRule(@PathVariable Long id, @RequestBody BreakRuleBody b) { return breaks.updateRule(id, b); }

    @DeleteMapping("/break-rules/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBreakRule(@PathVariable Long id) { breaks.deleteRule(id); }
}
