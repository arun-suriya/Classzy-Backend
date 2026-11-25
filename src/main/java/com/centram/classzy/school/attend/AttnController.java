package com.centram.classzy.school.attend;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.attend.AttnDto.*;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school/attendance")
@PreAuthorize(Roles.WRITE)
public class AttnController {
    private final AttnService s;
    public AttnController(AttnService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @GetMapping("/students") public List<StudentOpt> students(@RequestParam(name = "sectionId") Long sectionId) { return s.students(sectionId); }
    @GetMapping("/overview") public Overview overview(@RequestParam(name = "date", required = false) String date, @RequestParam(name = "classId", required = false) Long classId) { return s.overview(date, classId); }
    @GetMapping("/daily") public Daily daily(@RequestParam(name = "date") String date, @RequestParam(name = "sectionId") Long sectionId) { return s.daily(date, sectionId); }
    @PutMapping("/daily") public Daily save(@RequestBody DailyBody b) { return s.saveDaily(b); }
    @GetMapping("/reports/{type}") public Table report(@PathVariable("type") String type, @RequestParam(name = "from") String from, @RequestParam(name = "to") String to,
                                                      @RequestParam(name = "classId", required = false) Long classId, @RequestParam(name = "sectionId", required = false) Long sectionId) { return s.report(type, from, to, classId, sectionId); }
    @GetMapping("/leaves") public List<LeaveRow> leaves(@RequestParam(name = "from", required = false) String from, @RequestParam(name = "to", required = false) String to, @RequestParam(name = "status", required = false) String status) { return s.leaves(from, to, status); }
    @PostMapping("/leaves") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addLeave(@RequestBody LeaveBody b) { return s.addLeave(b); }
    @PostMapping("/leaves/{id}/{action}") public Map<String, Object> decide(@PathVariable("id") Long id, @PathVariable("action") String action) { return s.decide(id, action); }
    @GetMapping("/trends") public Trends trends(@RequestParam(name = "termId") Long termId, @RequestParam(name = "classId", required = false) Long classId) { return s.trends(termId, classId); }
    @GetMapping("/calendar") public Calendar calendar(@RequestParam(name = "studentId") Long studentId, @RequestParam(name = "month", required = false) String month) { return s.calendar(studentId, month); }
}
