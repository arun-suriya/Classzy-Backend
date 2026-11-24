package com.centram.classzy.school.academics;

import java.util.List;

import com.centram.classzy.school.admissions.AdmDto.ClassOpt;
import com.centram.classzy.school.admissions.AdmDto.Year;

public final class AcaDto {
    private AcaDto() {}

    public record Teacher(long id, String name) {}
    public record Sec(long id, long classId, String grade, long yearId, String name, Long teacherId, String teacher, String room, Integer max, long students, boolean active) {}
    public record SubjectOpt(long id, long yearId, String name, String code, String category) {}
    public record ClassSubject(long classId, long subjectId, String code, String name, String category, int periods) {}
    public record Options(List<Year> years, List<ClassOpt> classes, List<Sec> sections, List<SubjectOpt> subjects, List<ClassSubject> classSubjects, List<Teacher> teachers, List<String> categories) {}

    public record SubjectBody(Long classId, Long subjectId, String name, String code, String category, Integer periods) {}
    public record PeriodsBody(Integer periods) {}
    public record SecBody(Long yearId, Long classId, String newGrade, String name, Long teacherId, String room, Integer maxStudents) {}

    public record Slot(long id, String name, String type, String start, String end) {}
    public record Cell(long slotId, int dow, long subjectId, String subject, Long teacherId, String teacher) {}
    public record Load(long subjectId, String subject, int planned, int scheduled) {}
    public record Holiday(String name, String start, String end) {}
    public record Timetable(Sec section, List<Slot> slots, List<Cell> cells, List<Load> loads, String status, String publishedAt, List<Holiday> holidays) {}
    public record CellBody(Long sectionId, Long slotId, Integer dow, Long subjectId, Long teacherId) {}

    public record HwRow(long id, String title, String description, long sectionId, long classId, String grade, String section, long subjectId, String subject, String dueDate, String status, String createdBy, String createdAt) {}
    public record HwPage(List<HwRow> items, long total, int page, int size) {}
    public record HwBody(Long sectionId, Long subjectId, String title, String description, String dueDate, String status) {}
    public record AsgRow(long id, String title, String description, long sectionId, long classId, String grade, String section, long subjectId, String subject, String dueDate, int maxMarks, String status, long submitted, long graded, long total) {}
    public record AsgPage(List<AsgRow> items, long total, int page, int size) {}
    public record AsgBody(Long sectionId, Long subjectId, String title, String description, String dueDate, Integer maxMarks, String status) {}
    public record Submission(long studentId, String admissionNo, String name, boolean submitted, String submittedAt, Double marks, String remarks) {}
    public record SubBody(Boolean submitted, Double marks, String remarks) {}
    public record StatusBody(String status) {}
}
