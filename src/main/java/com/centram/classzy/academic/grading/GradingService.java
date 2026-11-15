package com.centram.classzy.academic.grading;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.centram.classzy.academic.grading.GradingDto.GradeRequest;
import com.centram.classzy.academic.grading.GradingDto.SystemRequest;
import com.centram.classzy.academic.grading.GradingDto.SystemResponse;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingService {

    private final GradingSystemRepository systems;
    private final GradeRepository grades;
    private final CurrentUser current;

    public GradingService(GradingSystemRepository systems, GradeRepository grades, CurrentUser current) {
        this.systems = systems;
        this.grades = grades;
        this.current = current;
    }

    // ---------- grading systems ----------

    @Transactional(readOnly = true)
    public List<SystemResponse> list() {
        Long inst = current.institutionId();
        List<GradingSystem> all = systems.findByInstitutionIdOrderByNameAsc(inst);
        Map<Long, List<Grade>> byUnit = grades.findByInstitutionIdOrderByMarksFromDesc(inst).stream()
                .collect(Collectors.groupingBy(Grade::getGradingSystemId));
        return all.stream().map(s -> SystemResponse.of(s, byUnit.getOrDefault(s.getId(), List.of()))).toList();
    }

    @Transactional
    public SystemResponse createSystem(SystemRequest r) {
        var s = new GradingSystem();
        s.setInstitutionId(current.institutionId());
        return saveSystem(s, r);
    }

    @Transactional
    public SystemResponse updateSystem(Long id, SystemRequest r) {
        return saveSystem(requireSystem(id), r);
    }

    @Transactional
    public void deleteSystem(Long id) {
        systems.delete(requireSystem(id));     // grades are removed by ON DELETE CASCADE
        systems.flush();
    }

    private SystemResponse saveSystem(GradingSystem s, SystemRequest r) {
        long id = s.getId() == null ? -1L : s.getId();
        String name = r.name().trim();
        if (systems.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(current.institutionId(), name, id)) {
            throw ApiException.conflict("A grading system named " + name + " already exists.");
        }
        s.setName(name);
        s.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        s.setActive(r.active());
        String type = r.gradeType() == null || r.gradeType().isBlank() ? s.getGradeType() : r.gradeType();
        if (!java.util.Set.of("GRADE_POINT", "PERCENTAGE", "CGPA").contains(type)) {
            throw ApiException.badRequest("Choose a valid grade type.");
        }
        int total = r.totalMarks() == null ? s.getTotalMarks() : r.totalMarks();
        int pass = r.passPercentage() == null ? s.getPassPercentage() : r.passPercentage();
        if (total < 1 || total > 1000) throw ApiException.badRequest("Total marks must be between 1 and 1000.");
        if (pass < 0 || pass > 100) throw ApiException.badRequest("Pass percentage must be between 0 and 100.");
        if ((r.classFrom() == null) != (r.classTo() == null)) throw ApiException.badRequest("Give both the first and last class, or leave both empty.");
        if (r.classFrom() != null && (r.classFrom() < 0 || r.classTo() > 12 || r.classFrom() > r.classTo())) {
            throw ApiException.badRequest("Applicable classes must be a valid range within 0-12.");
        }
        s.setGradeType(type);
        s.setTotalMarks(total);
        s.setPassPercentage(pass);
        s.setClassFrom(r.classFrom());
        s.setClassTo(r.classTo());
        GradingSystem saved = systems.saveAndFlush(s);
        return SystemResponse.of(saved, grades.findByInstitutionIdOrderByMarksFromDesc(current.institutionId()).stream()
                .filter(g -> g.getGradingSystemId().equals(saved.getId())).toList());
    }

    private GradingSystem requireSystem(Long id) {
        return systems.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Grading system"));
    }

    // ---------- grades inside a system ----------

    @Transactional
    public Grade addGrade(Long systemId, GradeRequest r) {
        requireSystem(systemId);
        var g = new Grade();
        g.setInstitutionId(current.institutionId());
        g.setGradingSystemId(systemId);
        return saveGrade(g, r);
    }

    @Transactional
    public Grade updateGrade(Long systemId, Long gradeId, GradeRequest r) {
        return saveGrade(requireGrade(systemId, gradeId), r);
    }

    @Transactional
    public void deleteGrade(Long systemId, Long gradeId) {
        grades.delete(requireGrade(systemId, gradeId));
        grades.flush();
    }

    private Grade requireGrade(Long systemId, Long gradeId) {
        return grades.findByIdAndGradingSystemIdAndInstitutionId(gradeId, systemId, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Grade"));
    }

    private Grade saveGrade(Grade g, GradeRequest r) {
        long id = g.getId() == null ? -1L : g.getId();
        String label = r.grade().trim();
        if (r.marksFrom() > r.marksTo()) {
            throw ApiException.badRequest("'Marks From' can't be greater than 'Marks To'.");
        }
        if (grades.existsByGradingSystemIdAndGradeIgnoreCaseAndIdNot(g.getGradingSystemId(), label, id)) {
            throw ApiException.conflict("Grade " + label + " already exists in this grading system.");
        }
        if (grades.overlaps(g.getGradingSystemId(), id, r.marksFrom(), r.marksTo())) {
            throw ApiException.conflict("This mark range overlaps with another grade.");
        }
        g.setGrade(label);
        g.setMarksFrom(r.marksFrom());
        g.setMarksTo(r.marksTo());
        g.setGradePoint(r.gradePoint());
        g.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        return grades.saveAndFlush(g);
    }
}
