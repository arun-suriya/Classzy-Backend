package com.centram.classzy.school;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.SchoolDto.*;
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

/** School Admin portal. Institution Admins see their own school; a Super Admin sees the school chosen in X-Institution-Id. */
@RestController
@RequestMapping("/api/school")
@PreAuthorize(Roles.WRITE)
public class SchoolController {

    private final SchoolService service;

    public SchoolController(SchoolService service) { this.service = service; }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(name = "yearId", required = false) Long yearId, @RequestParam(name = "termId", required = false) Long termId) { return service.dashboard(yearId, termId); }

    @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED)
    public Event addEvent(@RequestBody EventBody b) { return service.addEvent(b); }

    @DeleteMapping("/events/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable("id") Long id) { service.deleteEvent(id); }

    @PostMapping("/leave") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addLeave(@RequestBody LeaveBody b) { return service.addLeave(b); }

    @GetMapping("/leave/pending")
    public List<LeaveRow> pendingLeave() { return service.pendingLeave(); }

    @PostMapping("/leave/{id}/decide")
    public Map<String, Object> decideLeave(@PathVariable("id") Long id, @RequestBody DecisionBody b) { return service.decideLeave(id, b.approve()); }

    @GetMapping("/concessions/pending")
    public List<ConcessionRow> pendingConcessions() { return service.pendingConcessions(); }

    @PostMapping("/concessions/{id}/decide")
    public Map<String, Object> decideConcession(@PathVariable("id") Long id, @RequestBody DecisionBody b) { return service.decideConcession(id, b.approve()); }
}
