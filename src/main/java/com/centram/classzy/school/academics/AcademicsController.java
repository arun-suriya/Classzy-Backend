package com.centram.classzy.school.academics;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.academics.AcaDto.*;
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
@RequestMapping("/api/school/academics")
@PreAuthorize(Roles.WRITE)
public class AcademicsController {

    private final AcademicsService a;
    private final TimetableService t;
    private final WorkService w;

    public AcademicsController(AcademicsService a, TimetableService t, WorkService w) { this.a = a; this.t = t; this.w = w; }

    @GetMapping("/options") public Options options() { return a.options(); }

    // subjects of a grade
    @PostMapping("/subjects") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addSubject(@RequestBody SubjectBody b) { return a.addSubject(b); }
    @PutMapping("/classes/{classId}/subjects/{subjectId}") public void periods(@PathVariable("classId") Long c, @PathVariable("subjectId") Long s, @RequestBody PeriodsBody b) { a.setPeriods(c, s, b.periods()); }
    @DeleteMapping("/classes/{classId}/subjects/{subjectId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeSubject(@PathVariable("classId") Long c, @PathVariable("subjectId") Long s) { a.removeSubject(c, s); }

    // classes (sections)
    @GetMapping("/sections") public List<Sec> sections(@RequestParam(name = "yearId", required = false) Long y, @RequestParam(name = "classId", required = false) Long c) { return a.sections(y, c); }
    @PostMapping("/sections") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addSection(@RequestBody SecBody b) { return a.addSection(b); }
    @PutMapping("/sections/{id}") public Map<String, Object> updateSection(@PathVariable("id") Long id, @RequestBody SecBody b) { return a.updateSection(id, b); }
    @DeleteMapping("/sections/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSection(@PathVariable("id") Long id) { a.deleteSection(id); }

    // timetable
    @GetMapping("/timetable") public Timetable timetable(@RequestParam(name = "sectionId") Long sectionId) { return t.get(sectionId); }
    @PutMapping("/timetable/cell") public Map<String, Object> cell(@RequestBody CellBody b) { return t.setCell(b); }
    @PostMapping("/timetable/{sectionId}/publish") public Map<String, Object> publish(@PathVariable("sectionId") Long id) { return t.publish(id, true); }
    @PostMapping("/timetable/{sectionId}/unpublish") public Map<String, Object> unpublish(@PathVariable("sectionId") Long id) { return t.publish(id, false); }

    // homework
    @GetMapping("/homework")
    public HwPage homework(@RequestParam(name = "classId", required = false) Long c, @RequestParam(name = "sectionId", required = false) Long s, @RequestParam(name = "subjectId", required = false) Long sub,
                           @RequestParam(name = "status", required = false) String st, @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return w.homework(c, s, sub, st, p, z); }
    @PostMapping("/homework") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> createHw(@RequestBody HwBody b) { return w.createHomework(b); }
    @PutMapping("/homework/{id}") public Map<String, Object> updateHw(@PathVariable("id") Long id, @RequestBody HwBody b) { return w.updateHomework(id, b); }
    @PutMapping("/homework/{id}/status") public Map<String, Object> hwStatus(@PathVariable("id") Long id, @RequestBody StatusBody b) { return w.homeworkStatus(id, b.status()); }
    @DeleteMapping("/homework/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteHw(@PathVariable("id") Long id) { w.deleteHomework(id); }

    // assignments
    @GetMapping("/assignments")
    public AsgPage assignments(@RequestParam(name = "classId", required = false) Long c, @RequestParam(name = "sectionId", required = false) Long s, @RequestParam(name = "subjectId", required = false) Long sub,
                               @RequestParam(name = "status", required = false) String st, @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return w.assignments(c, s, sub, st, p, z); }
    @PostMapping("/assignments") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> createAsg(@RequestBody AsgBody b) { return w.createAssignment(b); }
    @PutMapping("/assignments/{id}") public Map<String, Object> updateAsg(@PathVariable("id") Long id, @RequestBody AsgBody b) { return w.updateAssignment(id, b); }
    @PutMapping("/assignments/{id}/status") public Map<String, Object> asgStatus(@PathVariable("id") Long id, @RequestBody StatusBody b) { return w.assignmentStatus(id, b.status()); }
    @DeleteMapping("/assignments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteAsg(@PathVariable("id") Long id) { w.deleteAssignment(id); }
    @GetMapping("/assignments/{id}/submissions") public List<Submission> subs(@PathVariable("id") Long id) { return w.submissions(id); }
    @PutMapping("/assignments/{id}/submissions/{studentId}") public Map<String, Object> saveSub(@PathVariable("id") Long id, @PathVariable("studentId") Long sid, @RequestBody SubBody b) { return w.saveSubmission(id, sid, b); }
}
