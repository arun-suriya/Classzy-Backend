package com.centram.classzy.exam;

import java.util.List;

import com.centram.classzy.exam.ExamDto.ReportBody;
import com.centram.classzy.exam.ExamDto.ReportView;
import com.centram.classzy.exam.ExamDto.ResultBody;
import com.centram.classzy.exam.ExamDto.ResultView;
import com.centram.classzy.exam.ExamDto.Settings;
import com.centram.classzy.exam.ExamDto.TypeBody;
import com.centram.classzy.exam.ExamDto.TypeView;
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

@RestController
@RequestMapping("/api/exam")
@PreAuthorize(Roles.READ)
public class ExamController {

    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public List<TypeView> types(@RequestParam Long academicYearId) { return service.listTypes(academicYearId); }

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

    @GetMapping("/report-templates")
    public List<ReportView> reports(@RequestParam Long academicYearId) { return service.listReports(academicYearId); }

    @PostMapping("/report-templates")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportView createReport(@RequestBody ReportBody b) { return service.createReport(b); }

    @PutMapping("/report-templates/{id}")
    @PreAuthorize(Roles.WRITE)
    public ReportView updateReport(@PathVariable Long id, @RequestBody ReportBody b) { return service.updateReport(id, b); }

    @DeleteMapping("/report-templates/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReport(@PathVariable Long id) { service.deleteReport(id); }

    @GetMapping("/result-templates")
    public List<ResultView> results(@RequestParam Long academicYearId) { return service.listResults(academicYearId); }

    @PostMapping("/result-templates")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResultView createResult(@RequestBody ResultBody b) { return service.createResult(b); }

    @PutMapping("/result-templates/{id}")
    @PreAuthorize(Roles.WRITE)
    public ResultView updateResult(@PathVariable Long id, @RequestBody ResultBody b) { return service.updateResult(id, b); }

    @DeleteMapping("/result-templates/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResult(@PathVariable Long id) { service.deleteResult(id); }

    @GetMapping("/settings")
    public Settings settings() { return service.getSettings(); }

    @PutMapping("/settings")
    @PreAuthorize(Roles.WRITE)
    public Settings saveSettings(@RequestBody Settings s) { return service.updateSettings(s); }
}
