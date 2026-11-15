package com.centram.classzy.academic.promotion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.centram.classzy.academic.classes.SchoolClass;
import com.centram.classzy.academic.classes.SchoolClassRepository;
import com.centram.classzy.academic.promotion.PromotionDto.OverrideRequest;
import com.centram.classzy.academic.promotion.PromotionDto.PreviewRow;
import com.centram.classzy.academic.promotion.PromotionDto.RuleRequest;
import com.centram.classzy.academic.year.AcademicYearService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.people.Student;
import com.centram.classzy.people.StudentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {

    private final PromotionRuleRepository rules;
    private final StudentProgressionRepository progressions;
    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final AcademicYearService years;
    private final CurrentUser current;

    public PromotionService(PromotionRuleRepository rules, StudentProgressionRepository progressions,
                            SchoolClassRepository classes, StudentRepository students,
                            AcademicYearService years, CurrentUser current) {
        this.rules = rules;
        this.progressions = progressions;
        this.classes = classes;
        this.students = students;
        this.years = years;
        this.current = current;
    }

    // ---------- rules ----------

    @Transactional(readOnly = true)
    public List<PromotionRule> listRules(Long academicYearId) {
        years.require(academicYearId);
        return rules.findByInstitutionIdAndAcademicYearIdOrderByIdAsc(current.institutionId(), academicYearId);
    }

    @Transactional
    public PromotionRule createRule(RuleRequest r) {
        var rule = new PromotionRule();
        rule.setInstitutionId(current.institutionId());
        return saveRule(rule, r);
    }

    @Transactional
    public PromotionRule updateRule(Long id, RuleRequest r) {
        return saveRule(requireRule(id), r);
    }

    @Transactional
    public void deleteRule(Long id) {
        rules.delete(requireRule(id));
        rules.flush();
    }

    private PromotionRule requireRule(Long id) {
        return rules.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Promotion rule"));
    }

    private PromotionRule saveRule(PromotionRule rule, RuleRequest r) {
        years.require(r.academicYearId());
        if (r.criteriaType() != CriteriaType.PASS_ALL && r.minValue() == null) {
            throw ApiException.badRequest("Minimum value is required for "
                    + (r.criteriaType() == CriteriaType.PERCENTAGE ? "percentage" : "grade point") + " criteria.");
        }
        String criteria = r.criteria() == null || r.criteria().isBlank() ? defaultCriteria(r) : r.criteria().trim();

        rule.setAcademicYearId(r.academicYearId());
        rule.setName(r.name().trim());
        rule.setFromClass(r.fromClass().trim());
        rule.setToClass(r.toClass().trim());
        rule.setCriteriaType(r.criteriaType());
        rule.setMinValue(r.criteriaType() == CriteriaType.PASS_ALL ? null : r.minValue());
        rule.setSubjectScope(r.subjectScope());
        rule.setCriteria(criteria);
        rule.setAdditionalConditions(r.additionalConditions() == null || r.additionalConditions().isBlank()
                ? null : r.additionalConditions().trim());
        rule.setActive(r.active());
        return rules.saveAndFlush(rule);
    }

    private static String defaultCriteria(RuleRequest r) {
        return switch (r.criteriaType()) {
            case PERCENTAGE -> "Minimum " + r.minValue() + "% in " + r.subjectScope().label();
            case GRADE_POINT -> "Minimum grade point " + r.minValue() + " in " + r.subjectScope().label();
            case PASS_ALL -> "Pass in all subjects";
        };
    }

    // ---------- student progression ----------

    /**
     * Lists each student of the year's classes with the proposed next class. Until the exams module records marks,
     * a student with no saved decision is shown as REVIEW and the next class comes from the first matching active rule.
     */
    @Transactional(readOnly = true)
    public List<PreviewRow> preview(Long academicYearId) {
        years.require(academicYearId);
        Long inst = current.institutionId();

        Map<Long, SchoolClass> classById = classes
                .findByInstitutionIdAndAcademicYearIdOrderByGradeLevelAscNameAsc(inst, academicYearId).stream()
                .collect(Collectors.toMap(SchoolClass::getId, Function.identity()));
        if (classById.isEmpty()) {
            return List.of();
        }
        List<Student> enrolled = students.findByInstitutionIdAndSchoolClassIdIn(inst, classById.keySet());
        Map<Long, StudentProgression> saved = progressions.findByInstitutionIdAndAcademicYearId(inst, academicYearId).stream()
                .collect(Collectors.toMap(StudentProgression::getStudentId, Function.identity(), (a, b) -> a));
        List<PromotionRule> activeRules = rules.findByInstitutionIdAndAcademicYearIdAndActiveTrueOrderByIdAsc(inst, academicYearId);

        return enrolled.stream()
                .sorted(Comparator.comparing(Student::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> {
                    String className = classById.get(s.getSchoolClassId()).getName();
                    StudentProgression p = saved.get(s.getId());
                    if (p != null) {
                        return new PreviewRow(s.getId(), s.getAdmissionNo(), s.getFullName(), className,
                                p.getNextClass(), p.getStatus(), p.getRemarks());
                    }
                    Optional<PromotionRule> rule = activeRules.stream()
                            .filter(x -> x.getFromClass().equalsIgnoreCase(className)).findFirst();
                    return new PreviewRow(s.getId(), s.getAdmissionNo(), s.getFullName(), className,
                            rule.map(PromotionRule::getToClass).orElse(null), ProgressionStatus.REVIEW,
                            rule.isPresent() ? "Awaiting results" : "No promotion rule for this class");
                }).toList();
    }

    @Transactional
    public PreviewRow override(OverrideRequest r) {
        Long inst = current.institutionId();
        years.require(r.academicYearId());
        Student s = students.findByIdAndInstitutionId(r.studentId(), inst)
                .orElseThrow(() -> ApiException.notFound("Student"));
        StudentProgression p = progressions.findByAcademicYearIdAndStudentId(r.academicYearId(), s.getId())
                .orElseGet(() -> {
                    var n = new StudentProgression();
                    n.setInstitutionId(inst);
                    n.setAcademicYearId(r.academicYearId());
                    n.setStudentId(s.getId());
                    return n;
                });
        p.setStatus(r.status());
        p.setNextClass(r.nextClass() == null || r.nextClass().isBlank() ? null : r.nextClass().trim());
        p.setRemarks(r.remarks() == null || r.remarks().isBlank() ? "Set manually" : r.remarks().trim());
        progressions.saveAndFlush(p);

        String className = s.getSchoolClassId() == null ? null
                : classes.findByIdAndInstitutionId(s.getSchoolClassId(), inst).map(SchoolClass::getName).orElse(null);
        return new PreviewRow(s.getId(), s.getAdmissionNo(), s.getFullName(), className,
                p.getNextClass(), p.getStatus(), p.getRemarks());
    }
}
