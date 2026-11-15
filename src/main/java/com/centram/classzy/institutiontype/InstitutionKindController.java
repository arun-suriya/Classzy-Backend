package com.centram.classzy.institutiontype;

import java.util.List;

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
@RequestMapping("/api/institution-types")
@PreAuthorize(Roles.SUPER)
public class InstitutionKindController {

    private final InstitutionKindService service;

    public InstitutionKindController(InstitutionKindService service) {
        this.service = service;
    }

    @GetMapping
    public List<InstitutionKindService.Row> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstitutionKindService.Row create(@Valid @RequestBody InstitutionKindService.Input body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public InstitutionKindService.Row update(@PathVariable Long id, @Valid @RequestBody InstitutionKindService.Input body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
