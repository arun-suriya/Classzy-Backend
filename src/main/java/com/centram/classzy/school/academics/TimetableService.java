package com.centram.classzy.school.academics;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.academics.AcaDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The weekly timetable of one section (Monday to Friday, using the school's periods from Timetable Settings).
 * It is a repeating weekly template: there are no per-date changes or substitutions.
 */
@Service
@Transactional
public class TimetableService {

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;
    private final AcademicsService base;

    public TimetableService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, AcademicsService base) {
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
        this.base = base;
    }

    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }

    @Transactional(readOnly = true)
    public Timetable get(Long sectionId) {
        long i = current.institutionId();
        Sec sec = base.section(sectionId);
        List<Slot> slots = jdbc.query("select id, name, slot_type, start_time, end_time from timetable_slots where institution_id = ? and active order by start_time, id",
                (rs, n) -> new Slot(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTime(4).toLocalTime().toString(), rs.getTime(5).toLocalTime().toString()), i);
        List<Cell> cells = jdbc.query("select e.slot_id, e.dow, e.subject_id, s.name, e.teacher_id, t.full_name from timetable_entries e join subjects s on s.id = e.subject_id left join teachers t on t.id = e.teacher_id where e.section_id = ? order by e.dow, e.slot_id",
                (rs, n) -> new Cell(rs.getLong(1), rs.getInt(2), rs.getLong(3), rs.getString(4), rs.getObject(5) == null ? null : rs.getLong(5), rs.getString(6)), sectionId);
        List<Load> loads = jdbc.query("select cs.subject_id, s.name, cs.periods_per_week, (select count(*) from timetable_entries e where e.section_id = ? and e.subject_id = cs.subject_id) from class_subjects cs join subjects s on s.id = cs.subject_id where cs.class_id = ? order by s.name",
                (rs, n) -> new Load(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4)), sectionId, sec.classId());
        Object[] pub = jdbc.query("select status, published_at from timetable_publish where section_id = ?", (rs, n) -> new Object[] { rs.getString(1), iso(rs.getTimestamp(2)) }, sectionId).stream().findFirst().orElse(new Object[] { "DRAFT", null });
        List<Holiday> hol = jdbc.query("select name, start_date, end_date from timetable_holidays where institution_id = ? and active and end_date >= current_date - 30 order by start_date",
                (rs, n) -> new Holiday(rs.getString(1), rs.getDate(2).toLocalDate().toString(), rs.getDate(3).toLocalDate().toString()), i);
        return new Timetable(sec, slots, cells, loads, (String) pub[0], (String) pub[1], hol);
    }

    private void touch(long sectionId) {
        jdbc.update("insert into timetable_publish (section_id, institution_id, status) values (?, ?, 'DRAFT') on conflict (section_id) do update set status = 'DRAFT'", sectionId, current.institutionId());
    }

    /** Sets one period of the week. A null subject clears it. Editing moves a published timetable back to draft. */
    public Map<String, Object> setCell(CellBody b) {
        long i = current.institutionId();
        if (b.sectionId() == null || b.slotId() == null || b.dow() == null) throw ApiException.badRequest("Choose a class, a period and a day.");
        Sec sec = base.section(b.sectionId());
        if (b.dow() < 1 || b.dow() > 5) throw ApiException.badRequest("Timetables run Monday to Friday.");
        String type = jdbc.query("select slot_type from timetable_slots where id = ? and institution_id = ? and active", (rs, n) -> rs.getString(1), b.slotId(), i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Period"));
        if (!"TEACHING".equals(type)) throw ApiException.badRequest("Only teaching periods can hold a subject.");
        if (b.subjectId() == null) {
            jdbc.update("delete from timetable_entries where section_id = ? and slot_id = ? and dow = ?", b.sectionId(), b.slotId(), b.dow());
            touch(b.sectionId());
            return Map.of("ok", true);
        }
        if (base.count("select count(*) from class_subjects where class_id = ? and subject_id = ?", sec.classId(), b.subjectId()) == 0) throw new FieldErrorsException(Map.of("subjectId", "Add this subject to " + sec.grade() + " first (Subjects tab)."));
        if (b.teacherId() != null) {
            if (base.count("select count(*) from teachers where id = ? and institution_id = ? and active", b.teacherId(), i) == 0) throw new FieldErrorsException(Map.of("teacherId", "Choose a teacher from the list."));
            List<String> clash = jdbc.query("select c.name || ' ' || s.name from timetable_entries e join sections s on s.id = e.section_id join school_classes c on c.id = s.school_class_id where e.teacher_id = ? and e.slot_id = ? and e.dow = ? and e.section_id <> ?",
                    (rs, n) -> rs.getString(1), b.teacherId(), b.slotId(), b.dow(), b.sectionId());
            if (!clash.isEmpty()) throw new FieldErrorsException(Map.of("teacherId", "This teacher already teaches " + clash.get(0) + " in this period."));
        }
        jdbc.update("insert into timetable_entries (institution_id, section_id, slot_id, dow, subject_id, teacher_id) values (?,?,?,?,?,?) on conflict (section_id, slot_id, dow) do update set subject_id = excluded.subject_id, teacher_id = excluded.teacher_id",
                i, b.sectionId(), b.slotId(), b.dow(), b.subjectId(), b.teacherId());
        touch(b.sectionId());
        return Map.of("ok", true);
    }

    public Map<String, Object> publish(Long sectionId, boolean publish) {
        long i = current.institutionId();
        Sec sec = base.section(sectionId);
        if (publish && base.count("select count(*) from timetable_entries where section_id = ?", sectionId) == 0) throw ApiException.conflict("The timetable is empty. Add some periods before publishing.");
        jdbc.update("insert into timetable_publish (section_id, institution_id, status, published_at, published_by) values (?,?,?,?,?) on conflict (section_id) do update set status = excluded.status, published_at = excluded.published_at, published_by = excluded.published_by",
                sectionId, i, publish ? "PUBLISHED" : "DRAFT", publish ? new Timestamp(System.currentTimeMillis()) : null, publish ? current.principal().email() : null);
        audit.record(publish ? "TIMETABLE_PUBLISHED" : "TIMETABLE_UNPUBLISHED", "SECTION", sectionId, i, "Timetable of " + sec.grade() + " " + sec.name() + (publish ? " published" : " moved back to draft"));
        return Map.of("status", publish ? "PUBLISHED" : "DRAFT");
    }
}
