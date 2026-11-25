package com.centram.classzy.school.staff;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.staff.StfDto.*;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school/staff")
@PreAuthorize(Roles.WRITE)
public class StfController {
    private final StfService s;
    public StfController(StfService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @GetMapping("/overview") public Overview overview() { return s.overview(); }
    @GetMapping("/directory") public StaffPage directory(@RequestParam(name = "q", required = false) String q, @RequestParam(name = "departmentId", required = false) Long departmentId, @RequestParam(name = "type", required = false) String type,
                                                          @RequestParam(name = "status", required = false) String status, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) { return s.directory(q, departmentId, type, status, page, size); }
    @PostMapping("/members") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> create(@RequestBody StaffBody b) { return s.create(b); }
    @PutMapping("/members/{id}") public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody StaffBody b) { return s.update(id, b); }
    @PostMapping("/members/{id}/active") public Map<String, Object> active(@PathVariable("id") Long id, @RequestParam(name = "value") boolean value) { return s.setActive(id, value); }

    @GetMapping("/departments") public List<Dept> departments() { return s.departments(); }
    @PostMapping("/departments") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addDept(@RequestBody DeptBody b) { return s.addDept(b); }
    @PutMapping("/departments/{id}") public Map<String, Object> updateDept(@PathVariable("id") Long id, @RequestBody DeptBody b) { return s.updateDept(id, b); }
    @DeleteMapping("/departments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDept(@PathVariable("id") Long id) { s.deleteDept(id); }

    @GetMapping("/leaves") public List<LeaveRow> leaves(@RequestParam(name = "from", required = false) String from, @RequestParam(name = "to", required = false) String to, @RequestParam(name = "status", required = false) String status) { return s.leaves(from, to, status); }
    @PostMapping("/leaves") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addLeave(@RequestBody LeaveBody b) { return s.addLeave(b); }
    @PostMapping("/leaves/{id}/{action}") public Map<String, Object> decide(@PathVariable("id") Long id, @PathVariable("action") String action) { return s.decideLeave(id, action); }

    @GetMapping("/cycles") public List<Cycle> cycles() { return s.cycles(); }
    @PostMapping("/cycles") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> startCycle(@RequestBody CycleBody b) { return s.startCycle(b); }
    @DeleteMapping("/cycles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCycle(@PathVariable("id") Long id) { s.deleteCycle(id); }
    @GetMapping("/appraisals") public List<Appraisal> appraisals(@RequestParam(name = "cycleId") Long cycleId, @RequestParam(name = "departmentId", required = false) Long departmentId) { return s.appraisals(cycleId, departmentId); }
    @PutMapping("/appraisals/{id}") public Map<String, Object> updateAppraisal(@PathVariable("id") Long id, @RequestBody AppraisalBody b) { return s.updateAppraisal(id, b); }

    @GetMapping("/trainings") public List<Training> trainings() { return s.trainings(); }
    @PostMapping("/trainings") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addTraining(@RequestBody TrainingBody b) { return s.addTraining(b); }
    @PutMapping("/trainings/{id}") public Map<String, Object> updateTraining(@PathVariable("id") Long id, @RequestBody TrainingBody b) { return s.updateTraining(id, b); }
    @PostMapping("/trainings/{id}/cancelled") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancel(@PathVariable("id") Long id, @RequestParam(name = "value") boolean value) { s.cancelTraining(id, value); }
    @DeleteMapping("/trainings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteTraining(@PathVariable("id") Long id) { s.deleteTraining(id); }

    @GetMapping("/vacancies") public List<Vacancy> vacancies() { return s.vacancies(); }
    @PostMapping("/vacancies") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addVacancy(@RequestBody VacancyBody b) { return s.addVacancy(b); }
    @PutMapping("/vacancies/{id}") public Map<String, Object> updateVacancy(@PathVariable("id") Long id, @RequestBody VacancyBody b) { return s.updateVacancy(id, b); }
    @PostMapping("/vacancies/{id}/open") @ResponseStatus(HttpStatus.NO_CONTENT) public void open(@PathVariable("id") Long id, @RequestParam(name = "value") boolean value) { s.setVacancyOpen(id, value); }
    @DeleteMapping("/vacancies/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteVacancy(@PathVariable("id") Long id) { s.deleteVacancy(id); }
    @GetMapping("/vacancies/{id}/applicants") public List<Applicant> applicants(@PathVariable("id") Long id) { return s.applicants(id); }
    @PostMapping("/vacancies/{id}/applicants") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addApplicant(@PathVariable("id") Long id, @RequestBody ApplicantBody b) { return s.addApplicant(id, b); }
    @PutMapping("/applicants/{id}") public Map<String, Object> updateApplicant(@PathVariable("id") Long id, @RequestBody ApplicantBody b) { return s.updateApplicant(id, b); }
    @DeleteMapping("/applicants/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteApplicant(@PathVariable("id") Long id) { s.deleteApplicant(id); }
}
