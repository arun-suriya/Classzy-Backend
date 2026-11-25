package com.centram.classzy.school.exams;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.exams.ExamDto.*;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school/exams")
@PreAuthorize(Roles.WRITE)
public class SchoolExamController {
    private final SchoolExamService s;
    private final SchoolResultService r;
    public SchoolExamController(SchoolExamService s, SchoolResultService r) { this.s = s; this.r = r; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @GetMapping("/overview") public Overview overview(@RequestParam(name = "yearId", required = false) Long y) { return s.overview(y); }
    @GetMapping public List<ExamRow> list(@RequestParam(name = "yearId", required = false) Long y) { return s.list(y); }
    @GetMapping("/{id}") public ExamRow get(@PathVariable("id") Long id) { return s.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> create(@RequestBody ExamBody b) { return s.create(b); }
    @PutMapping("/{id}") public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody ExamBody b) { return s.update(id, b); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable("id") Long id) { s.delete(id); }
    @PostMapping("/{id}/publish") public ExamRow publish(@PathVariable("id") Long id) { return s.publish(id); }
    @PostMapping("/{id}/unpublish") public ExamRow unpublish(@PathVariable("id") Long id) { return s.unpublish(id); }

    @GetMapping("/{id}/papers") public List<Paper> papers(@PathVariable("id") Long id, @RequestParam(name = "classId", required = false) Long classId) { return s.papers(id, classId); }
    @PostMapping("/{id}/generate-timetable") public GenResult generate(@PathVariable("id") Long id, @RequestBody GenBody b) { return s.generate(id, b); }
    @PutMapping("/papers/{paperId}") public Paper updatePaper(@PathVariable("paperId") Long id, @RequestBody PaperBody b) { return s.updatePaper(id, b); }
    @DeleteMapping("/papers/{paperId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deletePaper(@PathVariable("paperId") Long id) { s.deletePaper(id); }

    @GetMapping("/grade-bands") public List<Band> bands() { return s.bands(); }
    @PutMapping("/grade-bands") public List<Band> saveBands(@RequestBody BandsBody b) { return s.saveBands(b); }

    @GetMapping("/{id}/entry") public Entry entry(@PathVariable("id") Long id, @RequestParam(name = "subjectId") Long subjectId, @RequestParam(name = "sectionId") Long sectionId) { return r.entry(id, subjectId, sectionId); }
    @PutMapping("/{id}/entry") public Entry save(@PathVariable("id") Long id, @RequestBody EntryBody b) { return r.save(id, b); }
    @PostMapping("/{id}/entry/submit") public Entry submit(@PathVariable("id") Long id, @RequestBody Ref b) { return r.submit(id, b); }
    @PostMapping("/{id}/entry/approve") public Entry approve(@PathVariable("id") Long id, @RequestBody Ref b) { return r.approve(id, b); }
    @PostMapping("/{id}/entry/reopen") public Entry reopen(@PathVariable("id") Long id, @RequestBody Ref b) { return r.reopen(id, b); }

    @GetMapping("/{id}/reports/{type}") public Table report(@PathVariable("id") Long id, @PathVariable("type") String type, @RequestParam(name = "classId", required = false) Long classId) { return r.report(id, type, classId); }
}
