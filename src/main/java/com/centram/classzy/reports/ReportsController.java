package com.centram.classzy.reports;

import java.util.List;

import com.centram.classzy.reports.ReportsDto.Content;
import com.centram.classzy.reports.ReportsDto.GenerateBody;
import com.centram.classzy.reports.ReportsDto.Generated;
import com.centram.classzy.reports.ReportsDto.GeneratedRow;
import com.centram.classzy.reports.ReportsDto.Overview;
import com.centram.classzy.reports.ReportsDto.ScheduleBody;
import com.centram.classzy.reports.ReportsDto.ScheduleRow;
import com.centram.classzy.reports.ReportsDto.Template;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reports & Analytics (Super Admin). */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize(Roles.SUPER)
public class ReportsController {

    private final ReportsService service;

    public ReportsController(ReportsService service) { this.service = service; }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(name = "months", defaultValue = "6") int months, @RequestParam(name = "institutionId", required = false) Long institutionId,
                             @RequestParam(name = "module", required = false) String module, @RequestParam(name = "topDays", defaultValue = "30") int topDays) {
        return service.overview(months, institutionId, module, topDays);
    }

    @GetMapping("/templates")
    public List<Template> templates() { return service.templates(); }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Generated generate(@RequestBody GenerateBody body) { return service.generate(body); }

    @GetMapping("/generated")
    public List<GeneratedRow> recent(@RequestParam(name = "category", required = false) String category, @RequestParam(name = "limit", defaultValue = "10") int limit) { return service.recent(category, limit); }

    @GetMapping("/generated/{id}/content")
    public Content content(@PathVariable("id") Long id) { return service.content(id); }

    @DeleteMapping("/generated/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGenerated(@PathVariable("id") Long id) { service.deleteGenerated(id); }

    @GetMapping("/schedules")
    public List<ScheduleRow> schedules(@RequestParam(name = "category", required = false) String category) { return service.schedules(category); }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleRow createSchedule(@RequestBody ScheduleBody body) { return service.createSchedule(body); }

    @PutMapping("/schedules/{id}")
    public ScheduleRow updateSchedule(@PathVariable("id") Long id, @RequestBody ScheduleBody body) { return service.updateSchedule(id, body); }

    @PostMapping("/schedules/{id}/toggle")
    public ScheduleRow toggle(@PathVariable("id") Long id) { return service.toggle(id); }

    @PostMapping("/schedules/{id}/run")
    public Generated run(@PathVariable("id") Long id) { return service.runNow(id); }

    @DeleteMapping("/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(@PathVariable("id") Long id) { service.deleteSchedule(id); }
}
