package com.centram.classzy.attendance;

import java.util.List;

import com.centram.classzy.attendance.AttendanceDto.EventBody;
import com.centram.classzy.attendance.AttendanceDto.EventView;
import com.centram.classzy.attendance.AttendanceDto.RuleBody;
import com.centram.classzy.attendance.AttendanceDto.RuleView;
import com.centram.classzy.attendance.AttendanceDto.Settings;
import com.centram.classzy.attendance.AttendanceDto.TemplateBody;
import com.centram.classzy.attendance.AttendanceDto.TemplateView;
import com.centram.classzy.attendance.AttendanceDto.TypeBody;
import com.centram.classzy.attendance.AttendanceDto.TypeView;
import com.centram.classzy.security.Roles;

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
@RequestMapping("/api/attendance")
@PreAuthorize(Roles.READ)
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public Settings settings() { return service.getSettings(); }

    @PutMapping("/settings")
    @PreAuthorize(Roles.WRITE)
    public Settings saveSettings(@RequestBody Settings s) { return service.updateSettings(s); }

    @GetMapping("/types")
    public List<TypeView> types() { return service.listTypes(); }

    @PostMapping("/types")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public TypeView createType(@RequestBody TypeBody b) { return service.createType(b); }

    @PutMapping("/types/{id}")
    @PreAuthorize(Roles.WRITE)
    public TypeView updateType(@PathVariable Long id, @RequestBody TypeBody b) { return service.updateType(id, b); }

    @DeleteMapping("/types/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteType(@PathVariable Long id) { service.deleteType(id); }

    @GetMapping("/rules")
    public List<RuleView> rules() { return service.listRules(); }

    @PostMapping("/rules")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView createRule(@RequestBody RuleBody b) { return service.createRule(b); }

    @PutMapping("/rules/{id}")
    @PreAuthorize(Roles.WRITE)
    public RuleView updateRule(@PathVariable Long id, @RequestBody RuleBody b) { return service.updateRule(id, b); }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) { service.deleteRule(id); }

    @GetMapping("/notification-templates")
    public List<TemplateView> templates() { return service.listTemplates(); }

    @PostMapping("/notification-templates")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView createTemplate(@RequestBody TemplateBody b) { return service.createTemplate(b); }

    @PutMapping("/notification-templates/{id}")
    @PreAuthorize(Roles.WRITE)
    public TemplateView updateTemplate(@PathVariable Long id, @RequestBody TemplateBody b) { return service.updateTemplate(id, b); }

    @DeleteMapping("/notification-templates/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable Long id) { service.deleteTemplate(id); }

    @GetMapping("/notification-events")
    public List<EventView> events() { return service.listEvents(); }

    @PostMapping("/notification-events")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public EventView createEvent(@RequestBody EventBody b) { return service.createEvent(b); }

    @PutMapping("/notification-events/{id}")
    @PreAuthorize(Roles.WRITE)
    public EventView updateEvent(@PathVariable Long id, @RequestBody EventBody b) { return service.updateEvent(id, b); }

    @DeleteMapping("/notification-events/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable Long id) { service.deleteEvent(id); }
}
