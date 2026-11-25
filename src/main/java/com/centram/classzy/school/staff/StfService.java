package com.centram.classzy.school.staff;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.staff.StfDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff desk: directory, departments, leave, appraisals, training and recruitment. Staff are rows of the teachers table. */
@Service
@Transactional
public class StfService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    static final List<Code> TYPES = List.of(new Code("TEACHING", "Teaching"), new Code("NON_TEACHING", "Non-Teaching"));
    static final List<Code> LEAVE_TYPES = List.of(new Code("ANNUAL", "Annual Leave"), new Code("CASUAL", "Casual Leave"), new Code("MEDICAL", "Medical Leave"), new Code("SICK", "Sick Leave"), new Code("EARNED", "Earned Leave"), new Code("OTHER", "Other"));
    static final List<Desig> DESIGNATIONS = List.of(
        new Desig("TEACHING", "Teacher"), new Desig("TEACHING", "Senior Teacher"), new Desig("TEACHING", "Head of Department"), new Desig("TEACHING", "Coordinator"),
        new Desig("TEACHING", "Physical Education Teacher"), new Desig("TEACHING", "Vice Principal"), new Desig("TEACHING", "Principal"),
        new Desig("NON_TEACHING", "Admin Officer"), new Desig("NON_TEACHING", "Accountant"), new Desig("NON_TEACHING", "Office Clerk"), new Desig("NON_TEACHING", "Librarian"),
        new Desig("NON_TEACHING", "Lab Assistant"), new Desig("NON_TEACHING", "IT Support Officer"), new Desig("NON_TEACHING", "Counsellor"), new Desig("NON_TEACHING", "Nurse"),
        new Desig("NON_TEACHING", "Security Guard"), new Desig("NON_TEACHING", "Support Staff"));
    static final Set<String> APPRAISAL_STATUS = Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED");
    static final Set<String> APPLICANT_STATUS = Set.of("APPLIED", "SHORTLISTED", "REJECTED", "HIRED");

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public StfService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) { this.jdbc = jdbc; this.current = current; this.audit = audit; }

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static LocalDate today() { return LocalDate.now(IST); }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }
    private static String clip(String v, int max) { return v == null || v.trim().isEmpty() ? null : (v.trim().length() > max ? v.trim().substring(0, max) : v.trim()); }
    private static String label(List<Code> l, String c) { return l.stream().filter((x) -> x.code().equals(c)).map(Code::label).findFirst().orElse(c); }
    private static boolean has(List<Code> l, String c) { return c != null && l.stream().anyMatch((x) -> x.code().equals(c)); }
    private static String hm(Time t) { return t == null ? null : t.toString().substring(0, 5); }

    private static LocalDate parse(String v, String key, Map<String, String> err, boolean required) {
        if (v == null || v.isBlank()) { if (required) err.put(key, "Choose a date."); return null; }
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { err.put(key, "That isn't a valid date."); return null; }
    }
    private static void maxLen(Map<String, String> err, String key, String v, int max, String what) { if (v != null && v.trim().length() > max) err.put(key, what + " can be at most " + max + " characters."); }

    private void ownTeacher(Long id, String key, Map<String, String> err, boolean activeOnly) {
        if (id == null || count("select count(*) from teachers where id = ? and institution_id = ?" + (activeOnly ? " and active" : ""), id, inst()) == 0) err.put(key, "Choose a staff member from this school.");
    }
    private void ownDept(Long id, Map<String, String> err) {
        if (id != null && count("select count(*) from school_departments where id = ? and institution_id = ?", id, inst()) == 0) err.put("departmentId", "Choose a department from this school.");
    }

    // ------------------------------------------------------------------ options & overview

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        return new Options(
            jdbc.query("select id, name from school_departments where institution_id = ? order by name", (rs, n) -> new DeptOpt(rs.getLong(1), rs.getString(2)), i),
            DESIGNATIONS, TYPES, LEAVE_TYPES,
            jdbc.query("select id, staff_code, full_name, department_id, active from teachers where institution_id = ? order by full_name", (rs, n) -> new StaffOpt(rs.getLong(1), rs.getString(2), rs.getString(3), (Long) rs.getObject(4), rs.getBoolean(5)), i),
            today().toString());
    }

    private static final String LEAVE_SELECT = "select l.id, t.id, t.full_name, t.staff_code, l.leave_type, l.from_date, l.to_date, l.reason, l.status, l.decided_by from school_leave_requests l join teachers t on t.id = l.teacher_id where l.institution_id = ?";
    private LeaveRow leaveRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate a = rs.getDate(6).toLocalDate(), b = rs.getDate(7).toLocalDate();
        String t = rs.getString(5);
        return new LeaveRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), t, label(LEAVE_TYPES, t), a.toString(), b.toString(), (int) ChronoUnit.DAYS.between(a, b) + 1, rs.getString(8), rs.getString(9), rs.getString(10));
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        long i = inst();
        long total = count("select count(*) from teachers where institution_id = ? and active", i);
        long teaching = count("select count(*) from teachers where institution_id = ? and active and staff_type = 'TEACHING'", i);
        long onLeave = count("select count(distinct l.teacher_id) from school_leave_requests l join teachers t on t.id = l.teacher_id where l.institution_id = ? and t.active and l.status = 'APPROVED' and ? between l.from_date and l.to_date", i, Date.valueOf(today()));
        List<LeaveRow> up = jdbc.query(LEAVE_SELECT + " and l.status in ('APPROVED', 'PENDING') and l.to_date >= ? order by l.from_date, l.id limit 6", (rs, n) -> leaveRow(rs), i, Date.valueOf(today()));
        return new Overview(total, teaching, total - teaching, onLeave, up);
    }

    // ------------------------------------------------------------------ directory

    @Transactional(readOnly = true)
    public StaffPage directory(String q, Long departmentId, String type, String status, int page, int size) {
        StringBuilder w = new StringBuilder(" where t.institution_id = ?");
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (q != null && !q.isBlank()) { w.append(" and (lower(t.full_name) like ? or lower(coalesce(t.staff_code, '')) like ? or lower(coalesce(t.email, '')) like ?)"); String k = "%" + q.trim().toLowerCase(Locale.ROOT) + "%"; a.add(k); a.add(k); a.add(k); }
        if (departmentId != null) { w.append(" and t.department_id = ?"); a.add(departmentId); }
        if (type != null && !type.isBlank()) { w.append(" and t.staff_type = ?"); a.add(type.trim()); }
        if ("ACTIVE".equals(status)) w.append(" and t.active"); else if ("INACTIVE".equals(status)) w.append(" and not t.active");
        long total = count("select count(*) from teachers t" + w, a.toArray());
        List<Object> b = new ArrayList<>(a); b.add(Date.valueOf(today())); b.add(Math.max(1, Math.min(size, 100))); b.add(Math.max(0, page) * Math.max(1, Math.min(size, 100)));
        List<StaffRow> rows = jdbc.query("select t.id, t.staff_code, t.full_name, t.email, t.phone, t.department_id, d.name, t.designation, t.staff_type, t.joining_date, t.active, "
                + "exists (select 1 from school_leave_requests l where l.teacher_id = t.id and l.status = 'APPROVED' and ? between l.from_date and l.to_date) from teachers t left join school_departments d on d.id = t.department_id" + w + " order by t.active desc, t.full_name limit ? offset ?",
                (rs, n) -> staffRow(rs), reorder(b));
        return new StaffPage(rows, total);
    }
    /** The exists(...) placeholder comes first in the SQL, so its argument has to lead. */
    private static Object[] reorder(List<Object> b) { List<Object> o = new ArrayList<>(); o.add(b.get(b.size() - 3)); o.addAll(b.subList(0, b.size() - 3)); o.add(b.get(b.size() - 2)); o.add(b.get(b.size() - 1)); return o.toArray(); }
    private StaffRow staffRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date j = rs.getDate(10);
        String type = rs.getString(9);
        return new StaffRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), (Long) rs.getObject(6), rs.getString(7), rs.getString(8), type, label(TYPES, type), j == null ? null : j.toString(), rs.getBoolean(11), rs.getBoolean(12));
    }

    private void validate(StaffBody b, Long selfId, Map<String, String> err) {
        String name = clip(b.fullName(), 200);
        if (name == null || name.length() < 2) err.put("fullName", "Enter the staff member's full name.");
        maxLen(err, "fullName", b.fullName(), 200, "The name");
        String email = clip(b.email(), 200);
        if (email == null) err.put("email", "Enter a work email.");
        else if (!EMAIL.matcher(email).matches()) err.put("email", "Enter a valid email address.");
        else if (count("select count(*) from teachers where institution_id = ? and lower(email) = ? and id <> ?", inst(), email.toLowerCase(Locale.ROOT), selfId == null ? -1L : selfId) > 0) err.put("email", "Another staff member already uses this email.");
        String ph = clip(b.phone(), 30);
        if (ph != null && !ph.matches("^[0-9+()\\-\\s]{6,30}$")) err.put("phone", "Enter a valid phone number.");
        String type = b.staffType() == null ? "" : b.staffType().trim();
        if (!has(TYPES, type)) err.put("staffType", "Choose a staff type.");
        String d = clip(b.designation(), 60);
        if (d == null) err.put("designation", "Choose a designation.");
        else if (has(TYPES, type) && DESIGNATIONS.stream().noneMatch((x) -> x.staffType().equals(type) && x.name().equals(d))) err.put("designation", "That designation doesn't fit the staff type.");
        ownDept(b.departmentId(), err);
        if (b.departmentId() == null) err.putIfAbsent("departmentId", "Choose a department.");
        LocalDate j = parse(b.joiningDate(), "joiningDate", err, true);
        if (j != null && j.isAfter(today().plusYears(1))) err.put("joiningDate", "The joining date can't be more than a year ahead.");
    }

    private String nextCode() {
        Long max = jdbc.queryForObject("select coalesce(max(substring(staff_code from '[0-9]+$')::int), 0) from teachers where institution_id = ? and staff_code ~ '^STF-[0-9]+$'", Long.class, inst());
        return String.format("STF-%04d", (max == null ? 0 : max) + 1);
    }

    public Map<String, Object> create(StaffBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        validate(b, null, err); fail(err);
        String code = nextCode();
        Long id = jdbc.queryForObject("insert into teachers (institution_id, full_name, email, phone, active, staff_code, staff_type, designation, department_id, joining_date) values (?, ?, ?, ?, true, ?, ?, ?, ?, ?) returning id", Long.class,
                inst(), b.fullName().trim(), b.email().trim(), clip(b.phone(), 30), code, b.staffType().trim(), b.designation().trim(), b.departmentId(), Date.valueOf(LocalDate.parse(b.joiningDate().trim())));
        audit.record("STAFF_ADDED", "STAFF", id, inst(), "Staff added - " + b.fullName().trim());
        return Map.of("id", id, "code", code);
    }

    public Map<String, Object> update(Long id, StaffBody b) {
        if (count("select count(*) from teachers where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That staff member wasn't found.");
        Map<String, String> err = new LinkedHashMap<>();
        validate(b, id, err); fail(err);
        jdbc.update("update teachers set full_name = ?, email = ?, phone = ?, staff_type = ?, designation = ?, department_id = ?, joining_date = ? where id = ?",
                b.fullName().trim(), b.email().trim(), clip(b.phone(), 30), b.staffType().trim(), b.designation().trim(), b.departmentId(), Date.valueOf(LocalDate.parse(b.joiningDate().trim())), id);
        audit.record("STAFF_UPDATED", "STAFF", id, inst(), "Staff updated - " + b.fullName().trim());
        return Map.of("id", id);
    }

    public Map<String, Object> setActive(Long id, boolean active) {
        List<String> n = jdbc.queryForList("select full_name from teachers where id = ? and institution_id = ?", String.class, id, inst());
        if (n.isEmpty()) throw ApiException.notFound("That staff member wasn't found.");
        if (!active) {
            List<String> secs = jdbc.queryForList("select c.name || ' – ' || s.name from sections s join school_classes c on c.id = s.school_class_id where s.teacher_id = ? and s.active", String.class, id);
            if (!secs.isEmpty()) throw ApiException.conflict(n.get(0) + " is the class teacher of " + String.join(", ", secs) + ". Assign another class teacher first.");
            jdbc.update("update school_departments set head_id = null where head_id = ?", id);
        }
        jdbc.update("update teachers set active = ? where id = ?", active, id);
        audit.record(active ? "STAFF_ACTIVATED" : "STAFF_DEACTIVATED", "STAFF", id, inst(), (active ? "Staff reactivated - " : "Staff deactivated - ") + n.get(0));
        return Map.of("id", id, "active", active);
    }

    // ------------------------------------------------------------------ departments

    @Transactional(readOnly = true)
    public List<Dept> departments() {
        return jdbc.query("select d.id, d.name, d.head_id, h.full_name, (select count(*) from teachers t where t.department_id = d.id and t.active) from school_departments d left join teachers h on h.id = d.head_id where d.institution_id = ? order by d.name",
                (rs, n) -> new Dept(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3), rs.getString(4), rs.getLong(5)), inst());
    }

    private String deptName(DeptBody b, Long selfId, Map<String, String> err) {
        String name = clip(b.name(), 80);
        if (name == null) err.put("name", "Enter a department name.");
        maxLen(err, "name", b.name(), 80, "The name");
        if (name != null && count("select count(*) from school_departments where institution_id = ? and lower(name) = ? and id <> ?", inst(), name.toLowerCase(Locale.ROOT), selfId == null ? -1L : selfId) > 0) err.put("name", "A department with this name already exists.");
        if (b.headId() != null) ownTeacher(b.headId(), "headId", err, true);
        return name;
    }

    public Map<String, Object> addDept(DeptBody b) {
        Map<String, String> err = new LinkedHashMap<>(); String name = deptName(b, null, err); fail(err);
        Long id = jdbc.queryForObject("insert into school_departments (institution_id, name, head_id) values (?, ?, ?) returning id", Long.class, inst(), name, b.headId());
        audit.record("DEPARTMENT_ADDED", "DEPARTMENT", id, inst(), "Department added - " + name);
        return Map.of("id", id);
    }

    public Map<String, Object> updateDept(Long id, DeptBody b) {
        if (count("select count(*) from school_departments where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That department wasn't found.");
        Map<String, String> err = new LinkedHashMap<>(); String name = deptName(b, id, err); fail(err);
        jdbc.update("update school_departments set name = ?, head_id = ? where id = ?", name, b.headId(), id);
        audit.record("DEPARTMENT_UPDATED", "DEPARTMENT", id, inst(), "Department updated - " + name);
        return Map.of("id", id);
    }

    public void deleteDept(Long id) {
        List<String> n = jdbc.queryForList("select name from school_departments where id = ? and institution_id = ?", String.class, id, inst());
        if (n.isEmpty()) throw ApiException.notFound("That department wasn't found.");
        long staff = count("select count(*) from teachers where department_id = ?", id);
        if (staff > 0) throw ApiException.conflict(n.get(0) + " still has " + staff + " staff member" + (staff == 1 ? "" : "s") + ". Move them to another department first.");
        jdbc.update("delete from school_departments where id = ?", id);
        audit.record("DEPARTMENT_DELETED", "DEPARTMENT", id, inst(), "Department deleted - " + n.get(0));
    }

    // ------------------------------------------------------------------ leave

    @Transactional(readOnly = true)
    public List<LeaveRow> leaves(String fromS, String toS, String status) {
        StringBuilder q = new StringBuilder(LEAVE_SELECT);
        List<Object> a = new ArrayList<>(List.of(inst()));
        Map<String, String> err = new LinkedHashMap<>();
        LocalDate f = parse(fromS, "from", err, false), t = parse(toS, "to", err, false); fail(err);
        if (f != null) { q.append(" and l.to_date >= ?"); a.add(Date.valueOf(f)); }
        if (t != null) { q.append(" and l.from_date <= ?"); a.add(Date.valueOf(t)); }
        if (status != null && !status.isBlank()) { q.append(" and l.status = ?"); a.add(status.trim().toUpperCase(Locale.ROOT)); }
        q.append(" order by (l.status = 'PENDING') desc, l.from_date desc, l.id desc limit 300");
        return jdbc.query(q.toString(), (rs, n) -> leaveRow(rs), a.toArray());
    }

    public Map<String, Object> addLeave(LeaveBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        ownTeacher(b.teacherId(), "teacherId", err, true);
        LocalDate from = parse(b.from(), "from", err, true), to = parse(b.to(), "to", err, true);
        if (from != null && to != null) {
            if (to.isBefore(from)) err.put("to", "End date can't be before the start date.");
            else if (from.plusDays(365).isBefore(to)) err.put("to", "Leave can be at most a year.");
        }
        String type = b.type() == null || b.type().isBlank() ? "CASUAL" : b.type().trim();
        if (!has(LEAVE_TYPES, type)) err.put("type", "Choose a leave type.");
        maxLen(err, "reason", b.reason(), 300, "The reason");
        if (err.isEmpty() && count("select count(*) from school_leave_requests where teacher_id = ? and status in ('PENDING', 'APPROVED') and from_date <= ? and to_date >= ?", b.teacherId(), Date.valueOf(to), Date.valueOf(from)) > 0)
            err.put("from", "This staff member already has leave that overlaps these dates.");
        fail(err);
        Long id = jdbc.queryForObject("insert into school_leave_requests (institution_id, teacher_id, leave_type, from_date, to_date, reason) values (?, ?, ?, ?, ?, ?) returning id", Long.class, inst(), b.teacherId(), type, Date.valueOf(from), Date.valueOf(to), clip(b.reason(), 300));
        audit.record("LEAVE_REQUESTED", "LEAVE", id, inst(), "Staff leave recorded " + from + " to " + to);
        return Map.of("id", id);
    }

    public Map<String, Object> decideLeave(Long id, String action) {
        List<String> st = jdbc.queryForList("select status from school_leave_requests where id = ? and institution_id = ?", String.class, id, inst());
        if (st.isEmpty()) throw ApiException.notFound("That leave request wasn't found.");
        String act = action == null ? "" : action.trim().toUpperCase(Locale.ROOT), cur = st.get(0), next;
        switch (act) {
            case "APPROVE" -> { if (!"PENDING".equals(cur)) throw ApiException.conflict("Only pending requests can be approved."); next = "APPROVED"; }
            case "REJECT" -> { if (!"PENDING".equals(cur)) throw ApiException.conflict("Only pending requests can be rejected."); next = "REJECTED"; }
            case "CANCEL" -> { if (!"PENDING".equals(cur) && !"APPROVED".equals(cur)) throw ApiException.conflict("This request is already closed."); next = "CANCELLED"; }
            default -> throw ApiException.badRequest("Unknown action.");
        }
        jdbc.update("update school_leave_requests set status = ?, decided_by = ?, decided_at = now() where id = ?", next, actor(), id);
        audit.record("LEAVE_" + next, "LEAVE", id, inst(), "Staff leave " + next.toLowerCase(Locale.ROOT));
        return Map.of("id", id, "status", next);
    }

    // ------------------------------------------------------------------ performance

    @Transactional(readOnly = true)
    public List<Cycle> cycles() {
        return jdbc.query("select c.id, c.name, c.due_date, (select count(*) from school_staff_appraisals a where a.cycle_id = c.id), (select count(*) from school_staff_appraisals a where a.cycle_id = c.id and a.status = 'COMPLETED') from school_staff_cycles c where c.institution_id = ? order by c.created_at desc, c.id desc",
                (rs, n) -> new Cycle(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)), inst());
    }

    public Map<String, Object> startCycle(CycleBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = clip(b.name(), 60);
        if (name == null) err.put("name", "Enter a name for the cycle, for example Nov 2026.");
        maxLen(err, "name", b.name(), 60, "The name");
        if (name != null && count("select count(*) from school_staff_cycles where institution_id = ? and lower(name) = ?", inst(), name.toLowerCase(Locale.ROOT)) > 0) err.put("name", "A cycle with this name already exists.");
        LocalDate due = parse(b.dueDate(), "dueDate", err, true);
        if (due != null && due.isBefore(today())) err.put("dueDate", "The due date can't be in the past.");
        ownDept(b.departmentId(), err);
        fail(err);
        List<Object[]> staff = new ArrayList<>();
        jdbc.query("select t.id, case when d.head_id is not null and d.head_id <> t.id then d.head_id end from teachers t left join school_departments d on d.id = t.department_id where t.institution_id = ? and t.active" + (b.departmentId() == null ? "" : " and t.department_id = ?") + " order by t.id",
                (rs) -> { staff.add(new Object[] { rs.getLong(1), rs.getObject(2) }); }, b.departmentId() == null ? new Object[] { inst() } : new Object[] { inst(), b.departmentId() });
        if (staff.isEmpty()) throw ApiException.badRequest("There are no active staff to appraise" + (b.departmentId() == null ? "." : " in that department."));
        Long cid = jdbc.queryForObject("insert into school_staff_cycles (institution_id, name, due_date, created_by) values (?, ?, ?, ?) returning id", Long.class, inst(), name, Date.valueOf(due), actor());
        for (Object[] s : staff) jdbc.update("insert into school_staff_appraisals (institution_id, cycle_id, teacher_id, reviewer_id, due_date) values (?, ?, ?, ?, ?)", inst(), cid, s[0], s[1], Date.valueOf(due));
        audit.record("APPRAISAL_CYCLE_STARTED", "APPRAISAL_CYCLE", cid, inst(), "Appraisal cycle started - " + name);
        return Map.of("id", cid, "created", staff.size());
    }

    public void deleteCycle(Long id) {
        List<String> n = jdbc.queryForList("select name from school_staff_cycles where id = ? and institution_id = ?", String.class, id, inst());
        if (n.isEmpty()) throw ApiException.notFound("That cycle wasn't found.");
        if (count("select count(*) from school_staff_appraisals where cycle_id = ? and status <> 'NOT_STARTED'", id) > 0) throw ApiException.conflict("Some appraisals in this cycle have started, so it can't be deleted.");
        jdbc.update("delete from school_staff_cycles where id = ?", id);
        audit.record("APPRAISAL_CYCLE_DELETED", "APPRAISAL_CYCLE", id, inst(), "Appraisal cycle deleted - " + n.get(0));
    }

    @Transactional(readOnly = true)
    public List<Appraisal> appraisals(Long cycleId, Long departmentId) {
        if (cycleId == null) throw ApiException.badRequest("Choose an appraisal cycle.");
        StringBuilder q = new StringBuilder("select a.id, a.cycle_id, a.teacher_id, t.full_name, d.name, a.reviewer_id, r.full_name, a.due_date, a.status, a.rating, a.comments from school_staff_appraisals a join teachers t on t.id = a.teacher_id left join school_departments d on d.id = t.department_id left join teachers r on r.id = a.reviewer_id where a.institution_id = ? and a.cycle_id = ?");
        List<Object> a = new ArrayList<>(List.of(inst(), cycleId));
        if (departmentId != null) { q.append(" and t.department_id = ?"); a.add(departmentId); }
        q.append(" order by t.full_name");
        return jdbc.query(q.toString(), (rs, n) -> new Appraisal(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), (Long) rs.getObject(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getBigDecimal(10) == null ? null : rs.getBigDecimal(10).doubleValue(), rs.getString(11)), a.toArray());
    }

    public Map<String, Object> updateAppraisal(Long id, AppraisalBody b) {
        List<Long> t = jdbc.queryForList("select teacher_id from school_staff_appraisals where id = ? and institution_id = ?", Long.class, id, inst());
        if (t.isEmpty()) throw ApiException.notFound("That appraisal wasn't found.");
        Map<String, String> err = new LinkedHashMap<>();
        if (b.reviewerId() != null) { ownTeacher(b.reviewerId(), "reviewerId", err, true); if (b.reviewerId().equals(t.get(0))) err.put("reviewerId", "A person can't review their own appraisal."); }
        LocalDate due = parse(b.dueDate(), "dueDate", err, true);
        String st = b.status() == null ? "" : b.status().trim();
        if (!APPRAISAL_STATUS.contains(st)) err.put("status", "Choose a status.");
        Double rating = b.rating();
        if (rating != null && (rating < 1 || rating > 5 || Math.round(rating * 2) != rating * 2)) err.put("rating", "Rating must be from 1 to 5, in steps of 0.5.");
        if ("COMPLETED".equals(st)) { if (rating == null) err.putIfAbsent("rating", "Add a rating to complete the appraisal."); if (b.reviewerId() == null) err.putIfAbsent("reviewerId", "Choose a reviewer to complete the appraisal."); }
        maxLen(err, "comments", b.comments(), 1000, "Comments");
        fail(err);
        jdbc.update("update school_staff_appraisals set reviewer_id = ?, due_date = ?, status = ?, rating = ?, comments = ?, completed_at = case when ? = 'COMPLETED' then coalesce(completed_at, now()) else null end where id = ?",
                b.reviewerId(), Date.valueOf(due), st, rating, clip(b.comments(), 1000), st, id);
        audit.record("APPRAISAL_UPDATED", "APPRAISAL", id, inst(), "Appraisal updated (" + st + ")");
        return Map.of("id", id);
    }

    // ------------------------------------------------------------------ training

    private Training trainingRow(java.sql.ResultSet rs, Map<Long, List<Long>> people) throws java.sql.SQLException {
        long id = rs.getLong(1); LocalDate d = rs.getDate(3).toLocalDate();
        String status = rs.getBoolean(9) ? "CANCELLED" : d.isBefore(today()) ? "COMPLETED" : "SCHEDULED";
        List<Long> ids = people.getOrDefault(id, List.of());
        return new Training(id, rs.getString(2), d.toString(), hm(rs.getTime(4)), rs.getString(5), rs.getString(6), rs.getString(7), status, ids.size(), ids);
    }

    @Transactional(readOnly = true)
    public List<Training> trainings() {
        Map<Long, List<Long>> people = new java.util.HashMap<>();
        jdbc.query("select p.training_id, p.teacher_id from school_staff_training_people p join school_staff_trainings t on t.id = p.training_id where t.institution_id = ?", (rs) -> { people.computeIfAbsent(rs.getLong(1), (k) -> new ArrayList<>()).add(rs.getLong(2)); }, inst());
        return jdbc.query("select id, title, training_date, start_time, venue, trainer, description, null, cancelled from school_staff_trainings where institution_id = ? order by training_date desc, id desc limit 200", (rs, n) -> trainingRow(rs, people), inst());
    }

    private void validateTraining(TrainingBody b, Map<String, String> err) {
        if (clip(b.title(), 150) == null) err.put("title", "Enter the programme name.");
        maxLen(err, "title", b.title(), 150, "The title");
        parse(b.date(), "date", err, true);
        if (b.time() != null && !b.time().isBlank()) { try { LocalTime.parse(b.time().trim()); } catch (DateTimeParseException e) { err.put("time", "Enter a valid time."); } }
        maxLen(err, "venue", b.venue(), 100, "The venue"); maxLen(err, "trainer", b.trainer(), 100, "The trainer"); maxLen(err, "description", b.description(), 500, "The description");
        Set<Long> ids = b.teacherIds() == null ? Set.of() : new HashSet<>(b.teacherIds());
        if (ids.isEmpty()) err.put("teacherIds", "Choose at least one participant.");
        else if (count("select count(*) from teachers where institution_id = ? and active and id in (" + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")", concat(inst(), ids)) != ids.size()) err.put("teacherIds", "Some participants aren't active staff of this school.");
    }

    private static Object[] concat(long first, Set<Long> rest) { List<Object> o = new ArrayList<>(); o.add(first); o.addAll(rest); return o.toArray(); }

    private void people(Long id, List<Long> ids) {
        jdbc.update("delete from school_staff_training_people where training_id = ?", id);
        for (Long t : new HashSet<>(ids)) jdbc.update("insert into school_staff_training_people (training_id, teacher_id) values (?, ?)", id, t);
    }

    public Map<String, Object> addTraining(TrainingBody b) {
        Map<String, String> err = new LinkedHashMap<>(); validateTraining(b, err); fail(err);
        Long id = jdbc.queryForObject("insert into school_staff_trainings (institution_id, title, training_date, start_time, venue, trainer, description, created_by) values (?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                inst(), b.title().trim(), Date.valueOf(LocalDate.parse(b.date().trim())), b.time() == null || b.time().isBlank() ? null : Time.valueOf(LocalTime.parse(b.time().trim())), clip(b.venue(), 100), clip(b.trainer(), 100), clip(b.description(), 500), actor());
        people(id, b.teacherIds());
        audit.record("TRAINING_SCHEDULED", "TRAINING", id, inst(), "Training scheduled - " + b.title().trim());
        return Map.of("id", id);
    }

    public Map<String, Object> updateTraining(Long id, TrainingBody b) {
        if (count("select count(*) from school_staff_trainings where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That training wasn't found.");
        Map<String, String> err = new LinkedHashMap<>(); validateTraining(b, err); fail(err);
        jdbc.update("update school_staff_trainings set title = ?, training_date = ?, start_time = ?, venue = ?, trainer = ?, description = ? where id = ?", b.title().trim(), Date.valueOf(LocalDate.parse(b.date().trim())),
                b.time() == null || b.time().isBlank() ? null : Time.valueOf(LocalTime.parse(b.time().trim())), clip(b.venue(), 100), clip(b.trainer(), 100), clip(b.description(), 500), id);
        people(id, b.teacherIds());
        audit.record("TRAINING_UPDATED", "TRAINING", id, inst(), "Training updated - " + b.title().trim());
        return Map.of("id", id);
    }

    public void cancelTraining(Long id, boolean cancel) {
        if (jdbc.update("update school_staff_trainings set cancelled = ? where id = ? and institution_id = ?", cancel, id, inst()) == 0) throw ApiException.notFound("That training wasn't found.");
        audit.record(cancel ? "TRAINING_CANCELLED" : "TRAINING_RESTORED", "TRAINING", id, inst(), cancel ? "Training cancelled" : "Training restored");
    }

    public void deleteTraining(Long id) {
        if (jdbc.update("delete from school_staff_trainings where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That training wasn't found.");
        audit.record("TRAINING_DELETED", "TRAINING", id, inst(), "Training deleted");
    }

    // ------------------------------------------------------------------ recruitment

    @Transactional(readOnly = true)
    public List<Vacancy> vacancies() {
        return jdbc.query("select v.id, v.position, v.department_id, d.name, v.openings, v.description, v.closing_date, v.status, (select count(*) from school_staff_applicants a where a.vacancy_id = v.id), (select count(*) from school_staff_applicants a where a.vacancy_id = v.id and a.status = 'HIRED') "
                + "from school_staff_vacancies v left join school_departments d on d.id = v.department_id where v.institution_id = ? order by (v.status = 'OPEN') desc, v.created_at desc, v.id desc",
                (rs, n) -> { java.sql.Date c = rs.getDate(7); return new Vacancy(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3), rs.getString(4), rs.getInt(5), rs.getString(6), c == null ? null : c.toString(), rs.getString(8), rs.getLong(9), rs.getLong(10)); }, inst());
    }

    private LocalDate validateVacancy(VacancyBody b, Map<String, String> err) {
        if (clip(b.position(), 100) == null) err.put("position", "Enter the position.");
        maxLen(err, "position", b.position(), 100, "The position");
        ownDept(b.departmentId(), err);
        if (b.departmentId() == null) err.putIfAbsent("departmentId", "Choose a department.");
        if (b.openings() == null || b.openings() < 1 || b.openings() > 100) err.put("openings", "Openings must be from 1 to 100.");
        maxLen(err, "description", b.description(), 1000, "The description");
        LocalDate c = parse(b.closingDate(), "closingDate", err, false);
        if (c != null && c.isBefore(today())) err.put("closingDate", "The closing date can't be in the past.");
        return c;
    }

    public Map<String, Object> addVacancy(VacancyBody b) {
        Map<String, String> err = new LinkedHashMap<>(); LocalDate c = validateVacancy(b, err); fail(err);
        Long id = jdbc.queryForObject("insert into school_staff_vacancies (institution_id, position, department_id, openings, description, closing_date, created_by) values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                inst(), b.position().trim(), b.departmentId(), b.openings(), clip(b.description(), 1000), c == null ? null : Date.valueOf(c), actor());
        audit.record("VACANCY_ADDED", "VACANCY", id, inst(), "Vacancy added - " + b.position().trim());
        return Map.of("id", id);
    }

    public Map<String, Object> updateVacancy(Long id, VacancyBody b) {
        if (count("select count(*) from school_staff_vacancies where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That vacancy wasn't found.");
        Map<String, String> err = new LinkedHashMap<>(); LocalDate c = validateVacancy(b, err); fail(err);
        jdbc.update("update school_staff_vacancies set position = ?, department_id = ?, openings = ?, description = ?, closing_date = ? where id = ?", b.position().trim(), b.departmentId(), b.openings(), clip(b.description(), 1000), c == null ? null : Date.valueOf(c), id);
        audit.record("VACANCY_UPDATED", "VACANCY", id, inst(), "Vacancy updated - " + b.position().trim());
        return Map.of("id", id);
    }

    public void setVacancyOpen(Long id, boolean open) {
        if (jdbc.update("update school_staff_vacancies set status = ? where id = ? and institution_id = ?", open ? "OPEN" : "CLOSED", id, inst()) == 0) throw ApiException.notFound("That vacancy wasn't found.");
        audit.record(open ? "VACANCY_REOPENED" : "VACANCY_CLOSED", "VACANCY", id, inst(), open ? "Vacancy reopened" : "Vacancy closed");
    }

    public void deleteVacancy(Long id) {
        if (jdbc.update("delete from school_staff_vacancies where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That vacancy wasn't found.");
        audit.record("VACANCY_DELETED", "VACANCY", id, inst(), "Vacancy deleted");
    }

    private void ownVacancy(Long id) { if (count("select count(*) from school_staff_vacancies where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That vacancy wasn't found."); }

    @Transactional(readOnly = true)
    public List<Applicant> applicants(Long vacancyId) {
        ownVacancy(vacancyId);
        return jdbc.query("select id, vacancy_id, name, email, phone, status, notes, applied_at::date from school_staff_applicants where vacancy_id = ? order by applied_at desc, id desc", (rs, n) -> new Applicant(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)), vacancyId);
    }

    private void validateApplicant(ApplicantBody b, boolean needStatus, Map<String, String> err) {
        if (clip(b.name(), 120) == null) err.put("name", "Enter the applicant's name.");
        maxLen(err, "name", b.name(), 120, "The name");
        String e = clip(b.email(), 200);
        if (e != null && !EMAIL.matcher(e).matches()) err.put("email", "Enter a valid email address.");
        String ph = clip(b.phone(), 30);
        if (ph != null && !ph.matches("^[0-9+()\\-\\s]{6,30}$")) err.put("phone", "Enter a valid phone number.");
        if (e == null && ph == null) err.putIfAbsent("email", "Enter an email or a phone number.");
        maxLen(err, "notes", b.notes(), 300, "Notes");
        if (needStatus && !APPLICANT_STATUS.contains(b.status() == null ? "" : b.status().trim())) err.put("status", "Choose a status.");
    }

    public Map<String, Object> addApplicant(Long vacancyId, ApplicantBody b) {
        ownVacancy(vacancyId);
        Map<String, String> err = new LinkedHashMap<>(); validateApplicant(b, false, err); fail(err);
        Long id = jdbc.queryForObject("insert into school_staff_applicants (vacancy_id, name, email, phone, notes) values (?, ?, ?, ?, ?) returning id", Long.class, vacancyId, b.name().trim(), clip(b.email(), 200), clip(b.phone(), 30), clip(b.notes(), 300));
        audit.record("APPLICANT_ADDED", "APPLICANT", id, inst(), "Applicant added - " + b.name().trim());
        return Map.of("id", id);
    }

    public Map<String, Object> updateApplicant(Long id, ApplicantBody b) {
        if (count("select count(*) from school_staff_applicants a join school_staff_vacancies v on v.id = a.vacancy_id where a.id = ? and v.institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That applicant wasn't found.");
        Map<String, String> err = new LinkedHashMap<>(); validateApplicant(b, true, err); fail(err);
        jdbc.update("update school_staff_applicants set name = ?, email = ?, phone = ?, notes = ?, status = ? where id = ?", b.name().trim(), clip(b.email(), 200), clip(b.phone(), 30), clip(b.notes(), 300), b.status().trim(), id);
        audit.record("APPLICANT_UPDATED", "APPLICANT", id, inst(), "Applicant updated (" + b.status().trim() + ")");
        return Map.of("id", id);
    }

    public void deleteApplicant(Long id) {
        if (jdbc.update("delete from school_staff_applicants a using school_staff_vacancies v where v.id = a.vacancy_id and a.id = ? and v.institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That applicant wasn't found.");
        audit.record("APPLICANT_DELETED", "APPLICANT", id, inst(), "Applicant deleted");
    }
}
