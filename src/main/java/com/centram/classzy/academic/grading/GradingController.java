package com.centram.classzy.academic.grading;

import java.util.List;

import com.centram.classzy.academic.grading.GradingDto.GradeRequest;
import com.centram.classzy.academic.grading.GradingDto.GradeResponse;
import com.centram.classzy.academic.grading.GradingDto.SystemRequest;
import com.centram.classzy.academic.grading.GradingDto.SystemResponse;
import com.centram.classzy.security.Roles;

import jakarta.validation.Valid;

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
@RequestMapping("/api/grading-systems")
@PreAuthorize(Roles.READ)
public class GradingController {

    private final GradingService service;

    public GradingController(GradingService service) {
        this.service = service;
    }

    @GetMapping
    public List<SystemResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public SystemResponse create(@Valid @RequestBody SystemRequest r) {
        return service.createSystem(r);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.WRITE)
    public SystemResponse update(@PathVariable Long id, @Valid @RequestBody SystemRequest r) {
        return service.updateSystem(id, r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deleteSystem(id);
    }

    @PostMapping("/{id}/grades")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public GradeResponse addGrade(@PathVariable Long id, @Valid @RequestBody GradeRequest r) {
        return toResponse(service.addGrade(id, r));
    }

    @PutMapping("/{id}/grades/{gradeId}")
    @PreAuthorize(Roles.WRITE)
    public GradeResponse updateGrade(@PathVariable Long id, @PathVariable Long gradeId, @Valid @RequestBody GradeRequest r) {
        return toResponse(service.updateGrade(id, gradeId, r));
    }

    @DeleteMapping("/{id}/grades/{gradeId}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGrade(@PathVariable Long id, @PathVariable Long gradeId) {
        service.deleteGrade(id, gradeId);
    }

    private static GradeResponse toResponse(Grade g) {
        return new GradeResponse(g.getId(), g.getGrade(), g.getMarksFrom(), g.getMarksTo(),
                g.getGradePoint(), g.getDescription());
    }
}
