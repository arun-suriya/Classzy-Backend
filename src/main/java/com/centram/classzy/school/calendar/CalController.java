package com.centram.classzy.school.calendar;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.calendar.CalDto.*;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school/calendar")
@PreAuthorize(Roles.WRITE)
public class CalController {
    private final CalService s;
    public CalController(CalService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @GetMapping("/events")
    public List<Event> events(@RequestParam(name = "from") String from, @RequestParam(name = "to") String to, @RequestParam(name = "type", required = false) String type, @RequestParam(name = "audience", required = false) String audience) { return s.events(from, to, type, audience); }
    @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> add(@RequestBody Body b) { return s.add(b); }
    @PutMapping("/events/{id}") public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody Body b) { return s.update(id, b); }
    @DeleteMapping("/events/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable("id") Long id) { s.delete(id); }
}
