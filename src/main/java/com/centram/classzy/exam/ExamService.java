package com.centram.classzy.exam;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.academic.grading.GradingSystemRepository;
import com.centram.classzy.academic.year.AcademicYearRepository;
import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.exam.ExamDto.ReportBody;
import com.centram.classzy.exam.ExamDto.ReportView;
import com.centram.classzy.exam.ExamDto.ResultBody;
import com.centram.classzy.exam.ExamDto.ResultView;
import com.centram.classzy.exam.ExamDto.Settings;
import com.centram.classzy.exam.ExamDto.TypeBody;
import com.centram.classzy.exam.ExamDto.TypeView;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExamService {

    private static final Set<String> CATEGORIES = Set.of("INTERNAL", "EXTERNAL", "PRACTICAL");
    private static final Set<String> REPORT_TYPES = Set.of("TERM_WISE", "ANNUAL", "PERIODIC", "CERTIFICATE");
    private static final Set<String> SECTIONS = Set.of("STUDENT_DETAILS", "ATTENDANCE_SUMMARY", "SUBJECT_MARKS", "GRADES", "REMARKS",
            "TEACHER_COMMENTS", "PRINCIPAL_SIGNATURE", "SCHOOL_LOGO");
    private static final Set<String> CALCULATIONS = Set.of("WEIGHTED", "SUBJECT_BASED", "BOARD_PATTERN");
    private static final Set<String> ROUNDING = Set.of("NEAREST_WHOLE", "ROUND_UP", "ROUND_DOWN", "ONE_DECIMAL");
    private static final Set<String> DISPLAY = Set.of("GRADE_AND_MARKS", "MARKS_ONLY", "GRADE_ONLY");
    private static final Set<String> PUBLICATION = Set.of("MANUAL", "AUTO", "SCHEDULED");
    private static final Set<String> LEVELS = Set.of("CLASS_TEACHER", "ACADEMIC_COORDINATOR", "PRINCIPAL");
    private static final Set<String> PDF = Set.of("DEFAULT", "DETAILED", "COMPACT");

    private final CurrentUser current;
    private final ExamTypeRepository types;
    private final ReportTemplateRepository reports;
    private final ResultTemplateRepository results;
    private final ExamSettingsRepository settingsRepo;
    private final AcademicYearRepository years;
    private final GradingSystemRepository gradingSystems;
    private final AuditService audit;

    public ExamService(CurrentUser current, ExamTypeRepository types, ReportTemplateRepository reports, ResultTemplateRepository results,
                       ExamSettingsRepository settingsRepo, AcademicYearRepository years, GradingSystemRepository gradingSystems,
                       AuditService audit) {
        this.current = current;
        this.types = types;
        this.reports = reports;
        this.results = results;
        this.settingsRepo = settingsRepo;
        this.years = years;
        this.gradingSystems = gradingSystems;
        this.audit = audit;
    }

    private void requireYear(Long yearId, Long inst) {
        if (yearId == null) throw ApiException.badRequest("Choose an academic year first.");
        years.findByIdAndInstitutionId(yearId, inst).orElseThrow(() -> ApiException.notFound("Academic year"));
    }

    private static void classRange(Map<String, String> err, Integer from, Integer to) {
        if ((from == null) != (to == null)) err.put("classFrom", "Give both the first and last class, or leave both empty.");
        else if (from != null && (from < 0 || to > 12 || from > to)) err.put("classFrom", "Applicable classes must be a valid range within 0-12.");
    }

    // ------------------------------------------------------------------ exam types

    @Transactional(readOnly = true)
    public List<TypeView> listTypes(Long yearId) {
        Long inst = current.institutionId();
        requireYear(yearId, inst);
        return types.findByInstitutionIdAndAcademicYearIdOrderBySortOrderAscIdAsc(inst, yearId).stream().map(TypeView::of).toList();
    }

    public TypeView createType(TypeBody b) {
        Long inst = current.institutionId();
        requireYear(b.academicYearId(), inst);
        validateType(b, 0L);
        ExamType t = new ExamType();
        t.setInstitutionId(inst);
        t.academicYearId = b.academicYearId();
        t.sortOrder = (int) types.count() + 1;
        applyType(t, b);
        t = types.saveAndFlush(t);
        audit.record("EXAM_TYPE_CREATED", "ExamType", t.getId(), inst, "Exam type " + t.name + " created");
        return TypeView.of(t);
    }

    public TypeView updateType(Long id, TypeBody b) {
        Long inst = current.institutionId();
        ExamType t = types.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Exam type"));
        validateType(new TypeBody(t.academicYearId, b.name(), b.code(), b.description(), b.classFrom(), b.classTo(), b.weightage(), b.category(), b.active()), id);
        applyType(t, b);
        t = types.saveAndFlush(t);
        audit.record("EXAM_TYPE_UPDATED", "ExamType", id, inst, "Exam type " + t.name + " updated");
        return TypeView.of(t);
    }

    public void deleteType(Long id) {
        Long inst = current.institutionId();
        ExamType t = types.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Exam type"));
        if (results.usesExamType(id)) throw ApiException.conflict("This exam type is used by a result template. Remove it from the template first.");
        types.delete(t);
        audit.record("EXAM_TYPE_DELETED", "ExamType", id, inst, "Exam type " + t.name + " deleted");
    }

    private void validateType(TypeBody b, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        String code = trim(b.code());
        if (name.isEmpty()) err.put("name", "Exam type name is required.");
        else if (name.length() > 80) err.put("name", "Name is too long.");
        else if (types.existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(b.academicYearId(), name, self)) err.put("name", "An exam type with this name already exists.");
        if (code.isEmpty()) err.put("code", "Code is required.");
        else if (code.length() > 10) err.put("code", "Code can be at most 10 characters.");
        else if (types.existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(b.academicYearId(), code, self)) err.put("code", "An exam type with this code already exists.");
        if (b.weightage() < 0 || b.weightage() > 100) err.put("weightage", "Weightage must be between 0 and 100.");
        if (!CATEGORIES.contains(b.category())) err.put("category", "Choose a category.");
        classRange(err, b.classFrom(), b.classTo());
        if (!err.isEmpty()) throw new FieldErrorsException(err);
    }

    private void applyType(ExamType t, TypeBody b) {
        t.name = trim(b.name());
        t.code = trim(b.code()).toUpperCase();
        t.description = blank(b.description());
        t.classFrom = b.classFrom();
        t.classTo = b.classTo();
        t.weightage = b.weightage();
        t.category = b.category();
        t.active = b.active();
    }

    // ------------------------------------------------------------------ report templates

    @Transactional(readOnly = true)
    public List<ReportView> listReports(Long yearId) {
        Long inst = current.institutionId();
        requireYear(yearId, inst);
        return reports.findByInstitutionIdAndAcademicYearIdOrderByIdAsc(inst, yearId).stream().map(ReportView::of).toList();
    }

    public ReportView createReport(ReportBody b) {
        Long inst = current.institutionId();
        requireYear(b.academicYearId(), inst);
        validateReport(b, b.academicYearId(), 0L);
        ReportTemplate r = new ReportTemplate();
        r.setInstitutionId(inst);
        r.academicYearId = b.academicYearId();
        applyReport(r, b);
        r = reports.saveAndFlush(r);
        audit.record("REPORT_TEMPLATE_CREATED", "ReportTemplate", r.getId(), inst, "Report template " + r.name + " created");
        return ReportView.of(r);
    }

    public ReportView updateReport(Long id, ReportBody b) {
        Long inst = current.institutionId();
        ReportTemplate r = reports.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Report template"));
        validateReport(b, r.academicYearId, id);
        applyReport(r, b);
        r = reports.saveAndFlush(r);
        audit.record("REPORT_TEMPLATE_UPDATED", "ReportTemplate", id, inst, "Report template " + r.name + " updated");
        return ReportView.of(r);
    }

    public void deleteReport(Long id) {
        Long inst = current.institutionId();
        ReportTemplate r = reports.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Report template"));
        reports.delete(r);
        audit.record("REPORT_TEMPLATE_DELETED", "ReportTemplate", id, inst, "Report template " + r.name + " deleted");
    }

    private void validateReport(ReportBody b, Long yearId, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Report name is required.");
        else if (name.length() > 80) err.put("name", "Report name is too long.");
        else if (reports.existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(yearId, name, self)) err.put("name", "A report template with this name already exists.");
        if (!REPORT_TYPES.contains(b.reportType())) err.put("reportType", "Choose a report type.");
        if (b.sections() == null || !SECTIONS.containsAll(b.sections())) err.put("sections", "Invalid section selected.");
        classRange(err, b.classFrom(), b.classTo());
        if (!err.isEmpty()) throw new FieldErrorsException(err);
    }

    private void applyReport(ReportTemplate r, ReportBody b) {
        r.name = trim(b.name());
        r.reportType = b.reportType();
        r.classFrom = b.classFrom();
        r.classTo = b.classTo();
        r.sections = String.join(",", new LinkedHashSet<>(b.sections()));
        r.active = b.active();
    }

    // ------------------------------------------------------------------ result templates

    @Transactional(readOnly = true)
    public List<ResultView> listResults(Long yearId) {
        Long inst = current.institutionId();
        requireYear(yearId, inst);
        return results.findByInstitutionIdAndAcademicYearIdOrderByIdAsc(inst, yearId).stream().map(ResultView::of).toList();
    }

    public ResultView createResult(ResultBody b) {
        Long inst = current.institutionId();
        requireYear(b.academicYearId(), inst);
        validateResult(b, b.academicYearId(), inst, 0L);
        ResultTemplate r = new ResultTemplate();
        r.setInstitutionId(inst);
        r.academicYearId = b.academicYearId();
        applyResult(r, b);
        r = results.saveAndFlush(r);
        audit.record("RESULT_TEMPLATE_CREATED", "ResultTemplate", r.getId(), inst, "Result template " + r.name + " created");
        return ResultView.of(r);
    }

    public ResultView updateResult(Long id, ResultBody b) {
        Long inst = current.institutionId();
        ResultTemplate r = results.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Result template"));
        validateResult(b, r.academicYearId, inst, id);
        applyResult(r, b);
        r = results.saveAndFlush(r);
        audit.record("RESULT_TEMPLATE_UPDATED", "ResultTemplate", id, inst, "Result template " + r.name + " updated");
        return ResultView.of(r);
    }

    public void deleteResult(Long id) {
        Long inst = current.institutionId();
        ResultTemplate r = results.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Result template"));
        results.delete(r);
        audit.record("RESULT_TEMPLATE_DELETED", "ResultTemplate", id, inst, "Result template " + r.name + " deleted");
    }

    private void validateResult(ResultBody b, Long yearId, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Template name is required.");
        else if (name.length() > 80) err.put("name", "Template name is too long.");
        else if (results.existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(yearId, name, self)) err.put("name", "A result template with this name already exists.");
        if (!CALCULATIONS.contains(b.calculationType())) err.put("calculationType", "Choose a calculation type.");
        if (!ROUNDING.contains(b.roundingRule())) err.put("roundingRule", "Choose a rounding rule.");
        if (!DISPLAY.contains(b.displayFormat())) err.put("displayFormat", "Choose a display format.");
        classRange(err, b.classFrom(), b.classTo());
        List<Long> ids = b.examTypeIds() == null ? List.of() : b.examTypeIds();
        if ("WEIGHTED".equals(b.calculationType()) && ids.isEmpty()) err.put("examTypeIds", "Select at least one exam type.");
        for (Long id : ids) {
            boolean ok = types.findByIdAndInstitutionId(id, inst).map(t -> t.academicYearId.equals(yearId)).orElse(false);
            if (!ok) { err.put("examTypeIds", "One of the exam types is not part of this academic year."); break; }
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
    }

    private void applyResult(ResultTemplate r, ResultBody b) {
        r.name = trim(b.name());
        r.calculationType = b.calculationType();
        r.classFrom = b.classFrom();
        r.classTo = b.classTo();
        r.roundingRule = b.roundingRule();
        r.displayFormat = b.displayFormat();
        r.active = b.active();
        r.examTypeIds.clear();
        if (b.examTypeIds() != null) r.examTypeIds.addAll(b.examTypeIds());
    }

    // ------------------------------------------------------------------ additional settings

    public Settings getSettings() {
        Long inst = current.institutionId();
        return toSettings(ensure(inst));
    }

    public Settings updateSettings(Settings s) {
        Long inst = current.institutionId();
        ExamSettings e = ensure(inst);
        Map<String, String> err = new LinkedHashMap<>();
        if (!PUBLICATION.contains(s.publicationMethod())) err.put("publicationMethod", "Choose a publication method.");
        if (s.maxMarksPerSubject() < 1 || s.maxMarksPerSubject() > 1000) err.put("maxMarksPerSubject", "Enter a value between 1 and 1000.");
        if (s.defaultGradingId() != null && gradingSystems.findByIdAndInstitutionId(s.defaultGradingId(), inst).isEmpty()) err.put("defaultGradingId", "Grading system not found.");
        List<String> levels = s.approvalLevels() == null ? List.of() : s.approvalLevels();
        if (!LEVELS.containsAll(levels)) err.put("approvalLevels", "Invalid approval level.");
        if (s.reviewApproval() && levels.isEmpty()) err.put("approvalLevels", "Choose at least one approval level, or turn off review and approval.");
        if (!PDF.contains(s.pdfTemplate())) err.put("pdfTemplate", "Choose a PDF template.");
        if (s.workflowRemarks() != null && s.workflowRemarks().length() > 500) err.put("workflowRemarks", "Remarks can be at most 500 characters.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        e.publicationMethod = s.publicationMethod();
        e.showSubjectRank = s.showSubjectRank();
        e.allowReevaluation = s.allowReevaluation();
        e.defaultGradingId = s.defaultGradingId();
        e.maxMarksPerSubject = s.maxMarksPerSubject();
        e.considerAttendance = s.considerAttendance();
        e.teachersEnterMarks = s.teachersEnterMarks();
        e.reviewApproval = s.reviewApproval();
        e.editBeforePublish = s.editBeforePublish();
        e.notifyOnPublish = s.notifyOnPublish();
        e.reevaluationProcess = s.reevaluationProcess();
        e.approvalLevels = String.join(",", new LinkedHashSet<>(levels));
        e.workflowRemarks = blank(s.workflowRemarks());
        e.showGradeAndMarks = s.showGradeAndMarks();
        e.parentAccess = s.parentAccess();
        e.showTeacherComments = s.showTeacherComments();
        e.autoGeneratePdf = s.autoGeneratePdf();
        e.showAttendanceSummary = s.showAttendanceSummary();
        e.pdfTemplate = s.pdfTemplate();
        e.includeSchoolLogo = s.includeSchoolLogo();
        settingsRepo.save(e);
        audit.record("EXAM_SETTINGS_UPDATED", "ExamSettings", inst, inst, "Exam & grading additional settings updated");
        return toSettings(e);
    }

    private ExamSettings ensure(Long inst) {
        settingsRepo.createIfMissing(inst);
        return settingsRepo.findById(inst).orElseThrow(() -> ApiException.notFound("Exam settings"));
    }

    private Settings toSettings(ExamSettings e) {
        return new Settings(e.publicationMethod, e.showSubjectRank, e.allowReevaluation, e.defaultGradingId, e.maxMarksPerSubject,
                e.considerAttendance, e.teachersEnterMarks, e.reviewApproval, e.editBeforePublish, e.notifyOnPublish, e.reevaluationProcess,
                e.approvalLevels == null || e.approvalLevels.isBlank() ? List.of() : List.of(e.approvalLevels.split(",")), e.workflowRemarks,
                e.showGradeAndMarks, e.parentAccess, e.showTeacherComments, e.autoGeneratePdf, e.showAttendanceSummary, e.pdfTemplate, e.includeSchoolLogo);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
