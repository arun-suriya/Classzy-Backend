package com.centram.classzy.academic.year;

import java.util.List;

import com.centram.classzy.academic.year.YearDto.Request;
import com.centram.classzy.academic.year.YearDto.Response;
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
@RequestMapping("/api/academic-years")
@PreAuthorize(Roles.READ)
public class AcademicYearController {

    private final AcademicYearService service;

    public AcademicYearController(AcademicYearService service) {
        this.service = service;
    }

    @GetMapping
    public List<Response> list() {
        return service.list().stream().map(Response::of).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return Response.of(service.require(id));
    }

    @PostMapping
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request r) {
        return Response.of(service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.WRITE)
    public Response update(@PathVariable Long id, @Valid @RequestBody Request r) {
        return Response.of(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
