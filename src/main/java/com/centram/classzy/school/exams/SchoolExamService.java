package com.centram.classzy.school.exams;

import java.sql.Date;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.exams.ExamDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exams, their paper schedule and the grade bands. Marks and results are in SchoolResultService. */
@Service
@Transactional
public class SchoolExamService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final List<Code> TYPES = List.of(new Code("UNIT_TEST", "Unit Test"), new Code("TERM_EXAM", "Term Exam"), new Code("MID_TERM", "Mid Term"), new Code("FINAL", "Final Exam"), new Code("PRACTICAL", "Practical"));
    static final List<Code> SCALES = List.of(new Code("PERCENTAGE", "Percentage (0 – 100)"));

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public SchoolExamService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) { this.jdbc = jdbc; this.current = current; this.audit = audit; }

    private long inst() { return current.institutionId(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static LocalDate today() { return LocalDate.now(IST); }
    private static boolean has(List<Code> l, String c) { return c != null && l.stream().anyMatch((x) -> x.code().equals(c)); }
    private static String hm(Time t) { return t.toString().substring(0, 5); }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }

    // ------------------------------------------------------------------ options

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        return new Options(
            jdbc.query("select id, name, is_current from academic_years where institution_id = ? order by start_date desc", (rs, n) -> new YearOpt(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), i),
            jdbc.query("select id, academic_year_id, name from terms where institution_id = ? and active order by start_date", (rs, n) -> new TermOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i),
            jdbc.query("select id, academic_year_id, name from school_classes where institution_id = ? and active order by coalesce(grade_level, -1), name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i),
            jdbc.query("select s.id, c.id, c.name, s.name from sections s join school_classes c on c.id = s.school_class_id where s.institution_id = ? and s.active and c.active order by coalesce(c.grade_level, -1), c.name, s.name", (rs, n) -> new SectionOpt(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)), i),
            jdbc.query("select cs.class_id, su.id, su.name from class_subjects cs join subjects su on su.id = cs.subject_id where cs.institution_id = ? and su.active order by su.name", (rs, n) -> new ClassSubject(rs.getLong(1), rs.getLong(2), rs.getString(3)), i),
            TYPES, SCALES);
    }

    // ------------------------------------------------------------------ exams

    private static String stage(boolean published, LocalDate s, LocalDate e) {
        if (!published) return "DRAFT";
        LocalDate t = today();
        return t.isBefore(s) ? "SCHEDULED" : t.isAfter(e) ? "COMPLETED" : "IN_PROGRESS";
    }

    private static final String EXAM_COLS = "select x.id, x.name, x.academic_year_id, x.term_id, t.name, x.exam_type, x.start_date, x.end_date, x.grading_scale, x.pass_percent, x.status, x.created_by,"
            + " (select count(*) from school_exam_papers p where p.exam_id = x.id) from school_exams x left join terms t on t.id = x.term_id";

    private ExamRow row(java.sql.ResultSet rs, Map<Long, List<Long>> secs, Map<Long, String> labels) throws java.sql.SQLException {
        long id = rs.getLong(1);
        String type = rs.getString(6);
        boolean pub = "PUBLISHED".equals(rs.getString(11));
        LocalDate s = rs.getDate(7).toLocalDate(), e = rs.getDate(8).toLocalDate();
        return new ExamRow(id, rs.getString(2), rs.getLong(3), (Long) rs.getObject(4), rs.getString(5), type, TYPES.stream().filter((c) -> c.code().equals(type)).findFirst().map(Code::label).orElse(type), s.toString(), e.toString(), rs.getString(9),
                rs.getBigDecimal(10).doubleValue(), pub, stage(pub, s, e), secs.getOrDefault(id, List.of()), labels.getOrDefault(id, "—"), rs.getInt(13), rs.getString(12));
    }

    private List<ExamRow> exams(String where, Object... args) {
        long i = inst();
        Map<Long, List<Long>> secs = new LinkedHashMap<>();
        Map<Long, List<String>> names = new LinkedHashMap<>();
        jdbc.query("select es.exam_id, s.id, c.name, s.name from school_exam_sections es join school_exams x on x.id = es.exam_id join sections s on s.id = es.section_id join school_classes c on c.id = s.school_class_id where x.institution_id = ? order by coalesce(c.grade_level, -1), c.name, s.name",
                (rs) -> { secs.computeIfAbsent(rs.getLong(1), (k) -> new ArrayList<>()).add(rs.getLong(2)); names.computeIfAbsent(rs.getLong(1), (k) -> new ArrayList<>()).add(rs.getString(3) + " – " + rs.getString(4)); }, i);
        Map<Long, String> labels = new LinkedHashMap<>();
        names.forEach((k, v) -> labels.put(k, v.size() > 3 ? String.join(", ", v.subList(0, 3)) + " +" + (v.size() - 3) : String.join(", ", v)));
        List<Object> a = new ArrayList<>(List.of(i));
        a.addAll(List.of(args));
        return jdbc.query(EXAM_COLS + " where x.institution_id = ?" + where + " order by x.start_date desc, x.id desc", (rs, n) -> row(rs, secs, labels), a.toArray());
    }

    @Transactional(readOnly = true)
    public List<ExamRow> list(Long yearId) { return yearId == null ? exams("") : exams(" and x.academic_year_id = ?", yearId); }

    @Transactional(readOnly = true)
    public ExamRow get(Long id) { return exams(" and x.id = ?", id).stream().findFirst().orElseThrow(() -> ApiException.notFound("Exam")); }

    @Transactional(readOnly = true)
    public Overview overview(Long yearId) {
        long i = inst();
        Long y = yearId != null ? yearId : jdbc.query("select id from academic_years where institution_id = ? and is_current limit 1", (rs, n) -> rs.getLong(1), i).stream().findFirst().orElse(null);
        List<ExamRow> all = y == null ? List.of() : exams(" and x.academic_year_id = ?", y);
        long sched = all.stream().filter((e) -> e.stage().equals("SCHEDULED")).count(), prog = all.stream().filter((e) -> e.stage().equals("IN_PROGRESS")).count();
        long pending = y == null ? 0 : count("select count(*) from school_exam_results r join school_exams x on x.id = r.exam_id where x.institution_id = ? and x.academic_year_id = ? and r.status = 'SUBMITTED'", i, y);
        List<Upcoming> up = y == null ? List.of() : jdbc.query("select x.id, x.name || ' – ' || su.name, su.name, c.name, p.exam_date, x.start_date, x.end_date from school_exam_papers p join school_exams x on x.id = p.exam_id join subjects su on su.id = p.subject_id join school_classes c on c.id = p.class_id"
                + " where x.institution_id = ? and x.academic_year_id = ? and x.status = 'PUBLISHED' and p.exam_date >= ? order by p.exam_date, p.start_time, c.name limit 6",
                (rs, n) -> new Upcoming(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDate(5).toLocalDate().toString(), stage(true, rs.getDate(6).toLocalDate(), rs.getDate(7).toLocalDate())), i, y, Date.valueOf(today()));
        return new Overview(sched, prog, pending, up, all);
    }

    private record Clean(String name, long yearId, Long termId, String type, List<Long> sections, LocalDate s, LocalDate e, String scale, double pass) {}

    private Clean check(ExamBody b, Long selfId) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim();
        if (name.isEmpty()) e.put("name", "Required."); else if (name.length() > 120) e.put("name", "Use 120 characters or fewer.");
        if (b.yearId() == null || count("select count(*) from academic_years where id = ? and institution_id = ?", b.yearId(), i) == 0) e.put("yearId", "Choose an academic year.");
        else if (!name.isEmpty() && count("select count(*) from school_exams where institution_id = ? and academic_year_id = ? and lower(name) = lower(?) and id <> ?", i, b.yearId(), name, selfId == null ? -1L : selfId) > 0) e.put("name", "An exam with this name already exists for the year.");
        if (b.termId() != null && (b.yearId() == null || count("select count(*) from terms where id = ? and institution_id = ? and academic_year_id = ?", b.termId(), i, b.yearId()) == 0)) e.put("termId", "Choose a term of that year.");
        if (b.termId() == null) e.put("termId", "Choose a term.");
        if (!has(TYPES, b.type())) e.put("type", "Choose an exam type.");
        LocalDate s = null, en = null;
        try { s = LocalDate.parse(b.startDate() == null ? "" : b.startDate().trim()); } catch (DateTimeParseException x) { e.put("startDate", "Choose a start date."); }
        try { en = LocalDate.parse(b.endDate() == null ? "" : b.endDate().trim()); } catch (DateTimeParseException x) { e.put("endDate", "Choose an end date."); }
        if (s != null && en != null && en.isBefore(s)) e.put("endDate", "The end date can't be before the start date.");
        if (s != null && en != null && s.plusDays(120).isBefore(en)) e.put("endDate", "An exam can run for 120 days at most.");
        if (!has(SCALES, b.scale())) e.put("scale", "Percentage is the only grading scale supported for exams.");
        double pass = b.passPercent() == null ? 35 : b.passPercent();
        if (Double.isNaN(pass) || pass < 0 || pass > 100) e.put("passPercent", "Enter 0 to 100.");
        List<Long> secs = b.sectionIds() == null ? List.of() : b.sectionIds().stream().distinct().toList();
        if (secs.isEmpty()) e.put("sectionIds", "Choose at least one grade / section.");
        else if (b.yearId() != null && count("select count(*) from sections s join school_classes c on c.id = s.school_class_id where s.institution_id = ? and c.academic_year_id = ? and s.id in (" + secs.stream().map((x) -> "?").collect(Collectors.joining(",")) + ")",
                java.util.stream.Stream.concat(java.util.stream.Stream.of((Object) i, b.yearId()), secs.stream()).toArray()) != secs.size()) e.put("sectionIds", "Choose sections of the selected year.");
        fail(e);
        return new Clean(name, b.yearId(), b.termId(), b.type(), secs, s, en, b.scale(), pass);
    }

    public Map<String, Object> create(ExamBody b) {
        long i = inst();
        Clean c = check(b, null);
        Long id = jdbc.queryForObject("insert into school_exams (institution_id, name, academic_year_id, term_id, exam_type, start_date, end_date, grading_scale, pass_percent, created_by) values (?,?,?,?,?,?,?,?,?,?) returning id", Long.class,
                i, c.name(), c.yearId(), c.termId(), c.type(), Date.valueOf(c.s()), Date.valueOf(c.e()), c.scale(), c.pass(), current.principal().email());
        for (Long s : c.sections()) jdbc.update("insert into school_exam_sections (exam_id, section_id) values (?, ?)", id, s);
        audit.record("EXAM_CREATED", "EXAM", id, i, "Exam \"" + c.name() + "\" created as a draft");
        return Map.of("id", id);
    }

    private ExamRow editable(Long id) {
        ExamRow x = get(id);
        if (x.published()) throw ApiException.conflict("This exam's schedule is published. Unpublish it before changing the exam.");
        return x;
    }

    public Map<String, Object> update(Long id, ExamBody b) {
        long i = inst();
        editable(id);
        Clean c = check(b, id);
        jdbc.update("update school_exams set name = ?, academic_year_id = ?, term_id = ?, exam_type = ?, start_date = ?, end_date = ?, grading_scale = ?, pass_percent = ?, updated_at = now() where id = ?",
                c.name(), c.yearId(), c.termId(), c.type(), Date.valueOf(c.s()), Date.valueOf(c.e()), c.scale(), c.pass(), id);
        jdbc.update("delete from school_exam_sections where exam_id = ?", id);
        for (Long s : c.sections()) jdbc.update("insert into school_exam_sections (exam_id, section_id) values (?, ?)", id, s);
        // papers for grades that are no longer part of the exam, or outside its dates, no longer make sense
        jdbc.update("delete from school_exam_papers p where p.exam_id = ? and (not exists (select 1 from school_exam_sections es join sections s on s.id = es.section_id where es.exam_id = p.exam_id and s.school_class_id = p.class_id) or p.exam_date < ? or p.exam_date > ?)", id, Date.valueOf(c.s()), Date.valueOf(c.e()));
        audit.record("EXAM_UPDATED", "EXAM", id, i, "Exam \"" + c.name() + "\" updated");
        return Map.of("id", id);
    }

    public void delete(Long id) {
        ExamRow x = get(id);
        if (x.published()) throw ApiException.conflict("Unpublish the schedule before deleting this exam.");
        jdbc.update("delete from school_exams where id = ?", id);
        audit.record("EXAM_DELETED", "EXAM", id, inst(), "Exam \"" + x.name() + "\" deleted");
    }

    public ExamRow publish(Long id) {
        ExamRow x = get(id);
        if (x.published()) return x;
        if (x.papers() == 0) throw ApiException.conflict("Generate or add the exam papers before publishing the schedule.");
        jdbc.update("update school_exams set status = 'PUBLISHED', updated_at = now() where id = ?", id);
        audit.record("EXAM_PUBLISHED", "EXAM", id, inst(), "Schedule of \"" + x.name() + "\" published");
        return get(id);
    }

    public ExamRow unpublish(Long id) {
        ExamRow x = get(id);
        if (!x.published()) return x;
        if (count("select count(*) from school_exam_marks where exam_id = ?", id) > 0) throw ApiException.conflict("Marks have already been entered for this exam, so it can't go back to draft.");
        jdbc.update("update school_exams set status = 'DRAFT', updated_at = now() where id = ?", id);
        audit.record("EXAM_UNPUBLISHED", "EXAM", id, inst(), "Schedule of \"" + x.name() + "\" moved back to draft");
        return get(id);
    }

    // ------------------------------------------------------------------ papers

    private static final String PAPER_COLS = "select p.id, p.exam_id, p.class_id, c.name, p.subject_id, su.name, p.exam_date, p.start_time, p.end_time, p.room, p.max_marks from school_exam_papers p join school_classes c on c.id = p.class_id join subjects su on su.id = p.subject_id join school_exams x on x.id = p.exam_id";

    private static Paper paper(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Paper(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getLong(5), rs.getString(6), rs.getDate(7).toLocalDate().toString(), hm(rs.getTime(8)), hm(rs.getTime(9)), rs.getString(10), rs.getBigDecimal(11).doubleValue());
    }

    @Transactional(readOnly = true)
    public List<Paper> papers(Long examId, Long classId) {
        get(examId);
        List<Object> a = new ArrayList<>(List.of(examId, inst()));
        String w = " where p.exam_id = ? and x.institution_id = ?";
        if (classId != null) { w += " and p.class_id = ?"; a.add(classId); }
        return jdbc.query(PAPER_COLS + w + " order by p.exam_date, p.start_time, c.name, su.name", (rs, n) -> paper(rs), a.toArray());
    }

    /** Creates a paper for every subject of every grade in the exam that doesn't have one yet, one paper per grade per working day. */
    public GenResult generate(Long examId, GenBody b) {
        ExamRow x = editableForPapers(examId);
        LocalTime start;
        try { start = LocalTime.parse(b.startTime() == null || b.startTime().isBlank() ? "09:00" : b.startTime().trim()); } catch (DateTimeParseException e) { throw new FieldErrorsException(Map.of("startTime", "Choose a valid start time.")); }
        int minutes = b.minutes() == null ? 120 : b.minutes();
        if (minutes < 15 || minutes > 480 || start.plusMinutes(minutes).isBefore(start)) throw new FieldErrorsException(Map.of("minutes", "Use 15 to 480 minutes, ending before midnight."));
        LocalTime end = start.plusMinutes(minutes);
        Set<DayOfWeek> work = new HashSet<>();
        jdbc.query("select day_of_week from institution_working_days where institution_id = ?", (rs) -> { try { work.add(DayOfWeek.valueOf(rs.getString(1).toUpperCase())); } catch (IllegalArgumentException ignored) { /* unknown day */ } }, inst());
        if (work.isEmpty()) work.addAll(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = LocalDate.parse(x.startDate()); !d.isAfter(LocalDate.parse(x.endDate())); d = d.plusDays(1)) if (work.contains(d.getDayOfWeek())) days.add(d);
        List<Long> classIds = jdbc.query("select distinct s.school_class_id from school_exam_sections es join sections s on s.id = es.section_id where es.exam_id = ?", (rs, n) -> rs.getLong(1), examId);
        int created = 0, existing = 0;
        Map<String, String> short_ = new LinkedHashMap<>();
        for (Long cid : classIds) {
            List<Long> subjects = jdbc.query("select cs.subject_id from class_subjects cs join subjects su on su.id = cs.subject_id where cs.class_id = ? and su.active order by su.name", (rs, n) -> rs.getLong(1), cid);
            Set<Long> have = new HashSet<>(jdbc.query("select subject_id from school_exam_papers where exam_id = ? and class_id = ?", (rs, n) -> rs.getLong(1), examId, cid));
            Set<LocalDate> busy = new HashSet<>(jdbc.query("select exam_date from school_exam_papers where exam_id = ? and class_id = ?", (rs, n) -> rs.getDate(1).toLocalDate(), examId, cid));
            List<LocalDate> free = days.stream().filter((d) -> !busy.contains(d)).toList();
            List<Long> missing = subjects.stream().filter((s) -> !have.contains(s)).toList();
            existing += (int) subjects.stream().filter(have::contains).count();
            if (subjects.isEmpty()) { short_.put(jdbc.queryForObject("select name from school_classes where id = ?", String.class, cid), "has no subjects assigned (set them in Academics)"); continue; }
            if (missing.size() > free.size()) { short_.put(jdbc.queryForObject("select name from school_classes where id = ?", String.class, cid), "needs " + missing.size() + " free working days but the exam dates have " + free.size()); continue; }
            for (int k = 0; k < missing.size(); k++) {
                jdbc.update("insert into school_exam_papers (exam_id, class_id, subject_id, exam_date, start_time, end_time) values (?,?,?,?,?,?)", examId, cid, missing.get(k), Date.valueOf(free.get(k)), Time.valueOf(start), Time.valueOf(end));
                created++;
            }
        }
        if (!short_.isEmpty()) throw ApiException.conflict("Couldn't build the schedule: " + short_.entrySet().stream().map((e) -> e.getKey() + " " + e.getValue()).collect(Collectors.joining("; ")) + ". Nothing was added.");
        audit.record("EXAM_TIMETABLE", "EXAM", examId, inst(), "Timetable generated for \"" + x.name() + "\": " + created + " papers added");
        return new GenResult(created, existing);
    }

    private ExamRow editableForPapers(Long examId) {
        ExamRow x = get(examId);
        if (x.published()) throw ApiException.conflict("This exam's schedule is published. Unpublish it to change papers.");
        return x;
    }

    public Paper updatePaper(Long id, PaperBody b) {
        long i = inst();
        Paper p = jdbc.query(PAPER_COLS + " where p.id = ? and x.institution_id = ?", (rs, n) -> paper(rs), id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Paper"));
        ExamRow x = editableForPapers(p.examId());
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate d = null; LocalTime st = null, en = null;
        try { d = LocalDate.parse(b.date() == null ? "" : b.date().trim()); } catch (DateTimeParseException ex) { e.put("date", "Choose a date."); }
        try { st = LocalTime.parse(b.startTime() == null ? "" : b.startTime().trim()); } catch (DateTimeParseException ex) { e.put("startTime", "Choose a start time."); }
        try { en = LocalTime.parse(b.endTime() == null ? "" : b.endTime().trim()); } catch (DateTimeParseException ex) { e.put("endTime", "Choose an end time."); }
        if (d != null && (d.isBefore(LocalDate.parse(x.startDate())) || d.isAfter(LocalDate.parse(x.endDate())))) e.put("date", "Pick a date inside the exam (" + x.startDate() + " to " + x.endDate() + ").");
        if (st != null && en != null && !en.isAfter(st)) e.put("endTime", "The end time must be after the start time.");
        String room = b.room() == null || b.room().isBlank() ? null : b.room().trim();
        if (room != null && room.length() > 40) e.put("room", "Use 40 characters or fewer.");
        double max = b.maxMarks() == null ? 100 : b.maxMarks();
        if (Double.isNaN(max) || max <= 0 || max > 1000) e.put("maxMarks", "Enter a number from 1 to 1000.");
        fail(e);
        if (count("select count(*) from school_exam_papers where exam_id = ? and class_id = ? and id <> ? and exam_date = ? and start_time < ? and end_time > ?", p.examId(), p.classId(), id, Date.valueOf(d), Time.valueOf(en), Time.valueOf(st)) > 0)
            throw new FieldErrorsException(Map.of("date", p.className() + " already has another paper at that time."));
        if (room != null && count("select count(*) from school_exam_papers where exam_id = ? and id <> ? and lower(room) = lower(?) and exam_date = ? and start_time < ? and end_time > ?", p.examId(), id, room, Date.valueOf(d), Time.valueOf(en), Time.valueOf(st)) > 0)
            throw new FieldErrorsException(Map.of("room", "That room is already used by another paper at that time."));
        jdbc.update("update school_exam_papers set exam_date = ?, start_time = ?, end_time = ?, room = ?, max_marks = ? where id = ?", Date.valueOf(d), Time.valueOf(st), Time.valueOf(en), room, max, id);
        audit.record("EXAM_PAPER_UPDATED", "EXAM", p.examId(), i, p.subjectName() + " paper of " + p.className() + " updated");
        return jdbc.query(PAPER_COLS + " where p.id = ?", (rs, n) -> paper(rs), id).get(0);
    }

    public void deletePaper(Long id) {
        Paper p = jdbc.query(PAPER_COLS + " where p.id = ? and x.institution_id = ?", (rs, n) -> paper(rs), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Paper"));
        editableForPapers(p.examId());
        jdbc.update("delete from school_exam_papers where id = ?", id);
        audit.record("EXAM_PAPER_DELETED", "EXAM", p.examId(), inst(), p.subjectName() + " paper of " + p.className() + " removed");
    }

    // ------------------------------------------------------------------ grade bands

    static final List<Band> STANDARD = List.of(new Band("A", 90, 100, "Excellent"), new Band("B", 75, 89, "Very Good"), new Band("C", 60, 74, "Good"), new Band("D", 40, 59, "Satisfactory"), new Band("E", 0, 39, "Needs Improvement"));

    @Transactional(readOnly = true)
    public List<Band> bands() {
        return jdbc.query("select grade, min_pct, max_pct, remark from school_grade_bands where institution_id = ? and scale = 'PERCENTAGE' order by max_pct desc, min_pct desc", (rs, n) -> new Band(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getString(4)), inst());
    }

    public List<Band> saveBands(BandsBody b) {
        long i = inst();
        if (b.scale() != null && !b.scale().equals("PERCENTAGE")) throw new FieldErrorsException(Map.of("scale", "Percentage is the only grading scale supported."));
        List<Band> in = b.bands() == null ? List.of() : b.bands();
        Map<String, String> e = new LinkedHashMap<>();
        if (in.isEmpty()) e.put("bands", "Add at least one grade band.");
        Set<String> seen = new HashSet<>();
        for (int k = 0; k < in.size(); k++) {
            Band x = in.get(k);
            String g = x.grade() == null ? "" : x.grade().trim();
            if (g.isEmpty() || g.length() > 10) e.put("grade" + k, "Grade is required (10 characters at most).");
            else if (!seen.add(g.toLowerCase())) e.put("grade" + k, "Each grade can appear once.");
            if (x.min() == null || x.max() == null || x.min() < 0 || x.max() > 100) e.put("range" + k, "Use whole percentages from 0 to 100.");
            else if (x.max() < x.min()) e.put("range" + k, "Max can't be below min.");
            if (x.remark() != null && x.remark().trim().length() > 60) e.put("remark" + k, "Use 60 characters or fewer.");
        }
        if (e.isEmpty()) {
            List<Band> sorted = in.stream().sorted(java.util.Comparator.comparingInt(Band::min)).toList();
            if (sorted.get(0).min() != 0) e.put("bands", "The lowest band must start at 0%.");
            for (int k = 1; k < sorted.size() && !e.containsKey("bands"); k++) {
                int prevMax = sorted.get(k - 1).max(), min = sorted.get(k).min();
                if (min <= prevMax) e.put("bands", "Bands overlap: " + sorted.get(k - 1).grade() + " and " + sorted.get(k).grade() + ".");
                else if (min != prevMax + 1) e.put("bands", "There's a gap between " + sorted.get(k - 1).grade() + " (" + prevMax + "%) and " + sorted.get(k).grade() + " (" + min + "%).");
            }
            if (!e.containsKey("bands") && sorted.get(sorted.size() - 1).max() != 100) e.put("bands", "The highest band must end at 100%.");
        }
        fail(e);
        jdbc.update("delete from school_grade_bands where institution_id = ? and scale = 'PERCENTAGE'", i);
        for (Band x : in) jdbc.update("insert into school_grade_bands (institution_id, scale, grade, min_pct, max_pct, remark) values (?, 'PERCENTAGE', ?, ?, ?, ?)", i, x.grade().trim(), x.min(), x.max(), x.remark() == null || x.remark().isBlank() ? null : x.remark().trim());
        audit.record("GRADE_BANDS_SAVED", "EXAM", null, i, "Grade bands updated (" + in.size() + " bands)");
        return bands();
    }
}
