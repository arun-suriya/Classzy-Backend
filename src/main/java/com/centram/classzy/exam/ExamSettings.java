package com.centram.classzy.exam;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** One row per institution (Additional Settings tab). */
@Entity
@Table(name = "exam_settings")
public class ExamSettings {

    @Id
    @Column(name = "institution_id")
    public Long institutionId;

    public String publicationMethod = "MANUAL";
    public boolean showSubjectRank = true;
    public boolean allowReevaluation = true;
    @Column(name = "default_grading_id")
    public Long defaultGradingId;
    public int maxMarksPerSubject = 100;
    public boolean considerAttendance;

    public boolean teachersEnterMarks = true;
    public boolean reviewApproval = true;
    public boolean editBeforePublish = true;
    public boolean notifyOnPublish = true;
    public boolean reevaluationProcess = true;
    public String approvalLevels = "CLASS_TEACHER,PRINCIPAL";
    public String workflowRemarks;

    public boolean showGradeAndMarks = true;
    public boolean parentAccess = true;
    public boolean showTeacherComments = true;
    public boolean autoGeneratePdf = true;
    public boolean showAttendanceSummary = true;
    public String pdfTemplate = "DEFAULT";
    public boolean includeSchoolLogo = true;

    public Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
