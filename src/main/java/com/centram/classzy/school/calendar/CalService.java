package com.centram.classzy.school.calendar;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.calendar.CalDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** School calendar events. Reminders are stored on the event; nothing sends them yet. */
@Service
@Transactional
public class CalService {

    static final List<Code> TYPES = List.of(new Code("EXAM", "Exam / Test"), new Code("PARENT_MEETING", "Parent Meeting"), new Code("PTM", "PTM"), new Code("STAFF_MEETING", "Staff Meeting"), new Code("SCHOOL_EVENT", "School Event"), new Code("HOLIDAY", "Holiday"), new Code("OTHER", "Other"));
    static final List<Code> AUDIENCES = List.of(new Code("EVERYONE", "Everyone"), new Code("PARENTS", "All Parents"), new Code("STUDENTS", "All Students"), new Code("STAFF", "Staff"), new Code("CLASS", "Selected Grade / Section"));
    static final List<Code> REMINDERS = List.of(new Code("NONE", "No reminder"), new Code("15MIN", "15 minutes before"), new Code("1HOUR", "1 hour before"), new Code("1DAY", "1 day before"), new Code("1WEEK", "1 week before"));
    static final int MAX_DAYS = 100;

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public CalService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) { this.jdbc = jdbc; this.current = current; this.audit = audit; }

    private long inst() { return current.institutionId(); }
    private static String clip(String v, int max) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t); }
    private static boolean has(List<Code> l, String c) { return l.stream().anyMatch((x) -> x.code().equals(c)); }
    private static String up(String v) { return v == null ? "" : v.trim().toUpperCase(); }
    private static String hm(Time t) { return t == null ? null : t.toString().substring(0, 5); }

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        List<ClassOpt> classes = jdbc.query("select c.id, c.name from school_classes c join academic_years y on y.id = c.academic_year_id where c.institution_id = ? and c.active and y.is_current order by coalesce(c.grade_level, -1), c.name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getString(2)), i);
        List<SecOpt> secs = jdbc.query("select s.id, s.school_class_id, s.name from sections s join school_classes c on c.id = s.school_class_id join academic_years y on y.id = c.academic_year_id where s.institution_id = ? and s.active and y.is_current order by s.name", (rs, n) -> new SecOpt(rs.getLong(1), rs.getLong(2), rs.getString(3)), i);
        return new Options(TYPES, AUDIENCES, REMINDERS, classes, secs);
    }

    private static final String COLS = "select e.id, e.title, e.event_type, e.all_day, e.event_date, e.start_time, coalesce(e.end_date, e.event_date), e.end_time, e.audience, e.class_id, e.section_id, c.name, s.name, e.location, e.description, e.reminder, e.created_by"
            + " from school_events e left join school_classes c on c.id = e.class_id left join sections s on s.id = e.section_id";

    private static Event row(java.sql.ResultSet rs) throws java.sql.SQLException {
        String aud = rs.getString(9);
        String label = AUDIENCES.stream().filter((a) -> a.code().equals(aud)).findFirst().map(Code::label).orElse(aud);
        if (aud.equals("CLASS")) label = rs.getString(12) == null ? "Selected grade (removed)" : rs.getString(12) + (rs.getString(13) == null ? "" : " – " + rs.getString(13));
        return new Event(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getDate(5).toLocalDate().toString(), hm(rs.getTime(6)), (rs.getDate(7) == null ? rs.getDate(5) : rs.getDate(7)).toLocalDate().toString(), hm(rs.getTime(8)), aud,
                rs.getObject(10) == null ? null : rs.getLong(10), rs.getObject(11) == null ? null : rs.getLong(11), label, rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17));
    }

    /** Events that touch the date range [from, to]. */
    @Transactional(readOnly = true)
    public List<Event> events(String from, String to, String type, String audience) {
        LocalDate f, t;
        try { f = LocalDate.parse(from); t = LocalDate.parse(to); } catch (DateTimeParseException | NullPointerException x) { throw ApiException.badRequest("Use dates like 2026-11-30."); }
        if (t.isBefore(f)) throw ApiException.badRequest("The end of the range is before its start.");
        if (f.plusDays(MAX_DAYS).isBefore(t)) throw ApiException.badRequest("Ask for " + MAX_DAYS + " days or fewer at a time.");
        List<Object> a = new ArrayList<>(List.of(inst(), Date.valueOf(t), Date.valueOf(f)));
        StringBuilder w = new StringBuilder(" where e.institution_id = ? and e.event_date <= ? and coalesce(e.end_date, e.event_date) >= ?");
        if (type != null && !type.isBlank()) { w.append(" and e.event_type = ?"); a.add(up(type)); }
        if (audience != null && !audience.isBlank()) { w.append(" and e.audience = ?"); a.add(up(audience)); }
        return jdbc.query(COLS + w + " order by e.event_date, e.all_day desc, e.start_time nulls first, e.id", (rs, n) -> row(rs), a.toArray());
    }

    private record Clean(String title, String type, boolean allDay, LocalDate sd, LocalTime st, LocalDate ed, LocalTime et, String aud, Long classId, Long sectionId, String loc, String desc, String rem) {}

    private Clean check(Body b) {
        Map<String, String> e = new LinkedHashMap<>();
        String title = clip(b.title(), 150);
        if (title == null) e.put("title", "Required.");
        String type = up(b.type());
        if (!has(TYPES, type)) e.put("type", "Choose an event type.");
        boolean allDay = Boolean.TRUE.equals(b.allDay());
        LocalDate sd = null, ed = null;
        LocalTime st = null, et = null;
        try { sd = LocalDate.parse(b.startDate() == null ? "" : b.startDate().trim()); } catch (DateTimeParseException x) { e.put("startDate", "Choose a start date."); }
        try { ed = LocalDate.parse(b.endDate() == null ? "" : b.endDate().trim()); } catch (DateTimeParseException x) { e.put("endDate", "Choose an end date."); }
        if (sd != null && (sd.getYear() < 2000 || sd.getYear() > 2100)) e.put("startDate", "Choose a date between 2000 and 2100.");
        if (sd != null && ed != null && ed.isBefore(sd)) e.put("endDate", "The end date can't be before the start date.");
        if (ed != null && sd != null && ed.isAfter(sd.plusYears(1))) e.put("endDate", "An event can last a year at most.");
        if (!allDay) {
            try { st = LocalTime.parse(b.startTime() == null ? "" : b.startTime().trim()); } catch (DateTimeParseException x) { e.put("startTime", "Choose a start time."); }
            try { et = LocalTime.parse(b.endTime() == null ? "" : b.endTime().trim()); } catch (DateTimeParseException x) { e.put("endTime", "Choose an end time."); }
            if (sd != null && sd.equals(ed) && st != null && et != null && !et.isAfter(st)) e.put("endTime", "The end time must be after the start time.");
        }
        String aud = up(b.audience());
        Long classId = null, sectionId = null;
        if (!has(AUDIENCES, aud)) e.put("audience", "Choose an audience.");
        else if (aud.equals("CLASS")) {
            if (b.classId() == null) e.put("classId", "Choose a grade.");
            else if (jdbc.queryForObject("select count(*) from school_classes where id = ? and institution_id = ?", Long.class, b.classId(), inst()) == 0) e.put("classId", "Choose a grade of this school.");
            else {
                classId = b.classId();
                if (b.sectionId() != null) {
                    if (jdbc.queryForObject("select count(*) from sections where id = ? and institution_id = ? and school_class_id = ?", Long.class, b.sectionId(), inst(), classId) == 0) e.put("sectionId", "Choose a section of that grade.");
                    else sectionId = b.sectionId();
                }
            }
        }
        String rem = b.reminder() == null || b.reminder().isBlank() ? "NONE" : up(b.reminder());
        if (!has(REMINDERS, rem)) e.put("reminder", "Choose a reminder.");
        if (!e.isEmpty()) throw new FieldErrorsException(e);
        return new Clean(title, type, allDay, sd, st, ed, et, aud, classId, sectionId, clip(b.location(), 150), clip(b.description(), 2000), rem);
    }

    private static Object time(LocalTime t) { return t == null ? null : Time.valueOf(t); }

    public Map<String, Object> add(Body b) {
        Clean c = check(b);
        Long id = jdbc.queryForObject("insert into school_events (institution_id, title, event_type, all_day, event_date, start_time, end_date, end_time, audience, class_id, section_id, location, description, reminder, created_by) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id", Long.class,
                inst(), c.title(), c.type(), c.allDay(), Date.valueOf(c.sd()), time(c.st()), Date.valueOf(c.ed()), time(c.et()), c.aud(), c.classId(), c.sectionId(), c.loc(), c.desc(), c.rem(), current.principal().email());
        audit.record("EVENT_CREATED", "EVENT", id, inst(), "Calendar event \"" + c.title() + "\" added for " + c.sd());
        return Map.of("id", id);
    }

    public Map<String, Object> update(Long id, Body b) {
        if (jdbc.queryForObject("select count(*) from school_events where id = ? and institution_id = ?", Long.class, id, inst()) == 0) throw ApiException.notFound("Event");
        Clean c = check(b);
        jdbc.update("update school_events set title = ?, event_type = ?, all_day = ?, event_date = ?, start_time = ?, end_date = ?, end_time = ?, audience = ?, class_id = ?, section_id = ?, location = ?, description = ?, reminder = ?, updated_at = now() where id = ?",
                c.title(), c.type(), c.allDay(), Date.valueOf(c.sd()), time(c.st()), Date.valueOf(c.ed()), time(c.et()), c.aud(), c.classId(), c.sectionId(), c.loc(), c.desc(), c.rem(), id);
        audit.record("EVENT_UPDATED", "EVENT", id, inst(), "Calendar event \"" + c.title() + "\" updated");
        return Map.of("id", id);
    }

    public void delete(Long id) {
        String t = jdbc.query("select title from school_events where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("Event"));
        jdbc.update("delete from school_events where id = ?", id);
        audit.record("EVENT_DELETED", "EVENT", id, inst(), "Calendar event \"" + t + "\" deleted");
    }
}
