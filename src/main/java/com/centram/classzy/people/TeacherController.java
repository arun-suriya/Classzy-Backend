package com.centram.classzy.people;

import java.util.List;

import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.people.TeacherDto.Request;
import com.centram.classzy.people.TeacherDto.Response;
import com.centram.classzy.security.Roles;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/teachers")
@PreAuthorize(Roles.READ)
public class TeacherController {

    private final TeacherRepository repo;
    private final CurrentUser current;

    public TeacherController(TeacherRepository repo, CurrentUser current) {
        this.repo = repo;
        this.current = current;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Response> list() {
        return repo.findByInstitutionIdOrderByFullNameAsc(current.institutionId()).stream().map(Response::of).toList();
    }

    @PostMapping
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Response create(@Valid @RequestBody Request r) {
        var t = new Teacher();
        t.setInstitutionId(current.institutionId());
        return Response.of(save(t, r));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.WRITE)
    @Transactional
    public Response update(@PathVariable Long id, @Valid @RequestBody Request r) {
        return Response.of(save(require(id), r));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        repo.delete(require(id));
        repo.flush();     // a teacher still assigned to a section -> 409
    }

    private Teacher require(Long id) {
        return repo.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Teacher"));
    }

    private Teacher save(Teacher t, Request r) {
        t.setFullName(r.fullName().trim());
        t.setEmail(r.email() == null || r.email().isBlank() ? null : r.email().trim().toLowerCase());
        t.setPhone(r.phone() == null || r.phone().isBlank() ? null : r.phone().trim());
        t.setActive(r.active());
        return repo.saveAndFlush(t);
    }
}
