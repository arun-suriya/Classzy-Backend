package com.centram.classzy.branch;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Branches and campuses across institutions. Super Admin only for now. */
@RestController
@RequestMapping("/api/branches")
@PreAuthorize(Roles.SUPER)
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @GetMapping
    public List<BranchService.Row> list(@RequestParam(required = false) Long institutionId,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String type) {
        return service.list(institutionId, search, status, type);
    }

    @GetMapping("/{id}")
    public BranchService.Row get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchService.Row create(@Valid @RequestBody BranchService.Input body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public BranchService.Row update(@PathVariable Long id, @Valid @RequestBody BranchService.Input body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
