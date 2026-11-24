package com.centram.classzy.school.reports;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.reports.RptDto.*;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school/reports")
@PreAuthorize(Roles.WRITE)
public class RptController {
    private final RptService s;
    public RptController(RptService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @PostMapping("/run") public Table run(@RequestBody RunBody b) { return s.run(b); }
    @PostMapping("/custom/run") public Table runCustom(@RequestBody CustomBody b) { return s.runCustom(b); }
    @GetMapping("/custom") public List<Saved> saved() { return s.saved(); }
    @PostMapping("/custom") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> save(@RequestBody CustomBody b) { return s.save(b); }
    @DeleteMapping("/custom/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable("id") Long id) { s.delete(id); }
}
