package com.centram.classzy.usermgmt;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.usermgmt.UserMgmtDto.ActivityItem;
import com.centram.classzy.usermgmt.UserMgmtDto.Created;
import com.centram.classzy.usermgmt.UserMgmtDto.ResetResult;
import com.centram.classzy.usermgmt.UserMgmtDto.Summary;
import com.centram.classzy.usermgmt.UserMgmtDto.UserBody;
import com.centram.classzy.usermgmt.UserMgmtDto.UserDetail;
import com.centram.classzy.usermgmt.UserMgmtDto.UserPage;
import com.centram.classzy.usermgmt.UserMgmtDto.UserRow;
import com.centram.classzy.usermgmt.UserMgmtDto.YearOption;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Create, edit, suspend and reset accounts across institutions. Super Admin accounts are never listed or changed here. */
@Service
@Transactional
public class UserMgmtService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "SUSPENDED");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    private static final Set<String> SCOPES = Set.of("ASSIGNED", "ALL", "OWN");
    private static final Set<String> EVENTS = Set.of("WELCOME_EMAIL", "PASSWORD_RESET", "ANNOUNCEMENTS", "TIMETABLE", "ATTENDANCE", "EXAMS", "FEES", "TRANSPORT", "LIBRARY");
    private static final Set<String> VIA = Set.of("EMAIL", "SMS", "IN_APP");
    private static final Set<String> LANGS = Set.of("en", "hi", "ta", "te", "kn", "ml", "mr", "bn", "gu", "pa", "ur");

    private static final String FROM = " from users u join user_profiles p on p.user_id = u.id left join institutions i on i.id = u.institution_id left join academic_years y on y.id = p.academic_year_id where u.role <> 'SUPER_ADMIN'";
    private static final String ROLES_SQL = "(select string_agg(r.role_code, ',' order by r.is_primary desc, r.role_code) from user_roles r where r.user_id = u.id)";

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AuditService audit;

    public UserMgmtService(JdbcTemplate jdbc, CurrentUser current, PasswordEncoder encoder, PasswordPolicy policy, AuditService audit) {
        this.jdbc = jdbc;
        this.current = current;
        this.encoder = encoder;
        this.policy = policy;
        this.audit = audit;
    }

    /** The one institution an Institution Admin may manage; null for Super Admin. */
    private Long locked() { return current.principal().institutionId(); }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Summary summary(Long institutionId) {
        List<Object> args = new ArrayList<>();
        String scope = scopeSql(institutionId, args);
        Map<String, Long> byType = new LinkedHashMap<>(), fresh = new LinkedHashMap<>();
        for (String t : RoleCatalog.USER_TYPES) { byType.put(t, 0L); fresh.put(t, 0L); }
        long[] st = new long[3];
        jdbc.query("select u.user_type, u.status, (u.created_at >= date_trunc('month', now())) as fresh, count(*)" + FROM + scope + " group by 1, 2, 3", rs -> {
            String type = rs.getString(1);
            long n = rs.getLong(4);
            byType.merge(type, n, Long::sum);
            if (rs.getBoolean(3)) fresh.merge(type, n, Long::sum);
            switch (rs.getString(2)) { case "ACTIVE" -> st[0] += n; case "INACTIVE" -> st[1] += n; default -> st[2] += n; }
        }, args.toArray());
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        fresh.put("TOTAL", fresh.values().stream().mapToLong(Long::longValue).sum());
        return new Summary(total, byType, fresh, st[0], st[1], st[2]);
    }

    @Transactional(readOnly = true)
    public UserPage list(String type, Long institutionId, String status, String role, String q, int page, int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(scopeSql(institutionId, args));
        if (type != null && !type.isBlank()) { where.append(" and u.user_type = ?"); args.add(type.toUpperCase()); }
        if (status != null && !status.isBlank()) { where.append(" and u.status = ?"); args.add(status.toUpperCase()); }
        if (role != null && !role.isBlank()) { where.append(" and exists (select 1 from user_roles r where r.user_id = u.id and r.role_code = ?)"); args.add(role.toUpperCase()); }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%";
            where.append(" and (lower(u.full_name) like ? or lower(u.email) like ? or coalesce(p.phone, '') like ? or lower(coalesce(p.employee_id, '')) like ? or cast(u.id as text) = ?)");
            args.add(like); args.add(like); args.add(like); args.add(like); args.add(q.trim());
        }
        long total = jdbc.queryForObject("select count(*)" + FROM + where, Long.class, args.toArray());
        int s = Math.min(Math.max(size, 1), 100), p = Math.max(page, 0);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s); pageArgs.add((long) p * s);
        List<UserRow> rows = jdbc.query("select u.id, u.full_name, u.email, u.user_type, " + ROLES_SQL + " as roles, u.institution_id, i.name, u.status, u.last_login_at" + FROM + where
                + " order by u.created_at desc, u.id desc limit ? offset ?", (rs, n) -> new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), codes(rs.getString(5)),
                (Long) rs.getObject(6), rs.getString(7), rs.getString(8), ts(rs, 9)), pageArgs.toArray());
        return new UserPage(rows, total, p, s);
    }

    @Transactional(readOnly = true)
    public UserDetail get(Long id) { return detail(id); }

    @Transactional(readOnly = true)
    public List<ActivityItem> activity(Long id, String kind) {
        detail(id);
        String filter = "ACTIVITY".equalsIgnoreCase(kind) ? "action in ('LOGIN', 'LOGIN_FAILED')" : "action not in ('LOGIN', 'LOGIN_FAILED')";
        return jdbc.query("select created_at, actor_email, action, description from audit_logs where entity_type = 'USER' and entity_id = ? and " + filter + " order by created_at desc limit 50",
                (rs, n) -> new ActivityItem(rs.getTimestamp(1).toInstant().toString(), rs.getString(2), rs.getString(3), rs.getString(4)), id);
    }

    @Transactional(readOnly = true)
    public List<YearOption> years(Long institutionId) {
        Long inst = locked() != null ? locked() : institutionId;
        if (inst == null) return List.of();
        return jdbc.query("select id, name from academic_years where institution_id = ? order by start_date desc", (rs, n) -> new YearOption(rs.getLong(1), rs.getString(2)), inst);
    }

    // ------------------------------------------------------------------ writes

    public Created create(UserBody b) {
        Long inst = locked() != null ? locked() : b.institutionId();
        Clean c = clean(b, null, inst);
        String temp = null;
        String pw;
        if ("CUSTOM".equalsIgnoreCase(b.passwordMode())) {
            pw = b.password();
            String bad = policy.violation(inst, pw);
            if (bad != null) throw new FieldErrorsException(Map.of("password", bad));
        } else {
            pw = temp = policy.generate(inst);
        }
        Long id = jdbc.queryForObject("insert into users (institution_id, email, password_hash, full_name, role, active, user_type, status, valid_from, valid_to) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, inst, c.email, encoder.encode(pw), c.fullName, RoleCatalog.loginRole(c.type).name(), "ACTIVE".equals(c.status), c.type, c.status, c.validFrom, c.validTo);
        saveProfile(id, b, c);
        if (temp != null || !"CUSTOM".equalsIgnoreCase(b.passwordMode())) jdbc.update("update user_profiles set password_changed_at = now() where user_id = ?", id);
        audit.record("USER_CREATED", "USER", id, inst, c.fullName + " (" + c.type.toLowerCase() + ") account created");
        return new Created(detail(id), temp);
    }

    public UserDetail update(Long id, UserBody b) {
        Long inst = jdbc.queryForObject("select institution_id from users where id = ?", Long.class, requireId(id));
        Long target = locked() != null ? locked() : (b.institutionId() != null ? b.institutionId() : inst);
        Clean c = clean(b, id, target);
        if (id.equals(current.principal().userId()) && !"ACTIVE".equals(c.status)) throw ApiException.conflict("You can't deactivate your own account.");
        jdbc.update("update users set institution_id = ?, email = ?, full_name = ?, role = ?, active = ?, user_type = ?, status = ?, valid_from = ?, valid_to = ? where id = ?",
                target, c.email, c.fullName, RoleCatalog.loginRole(c.type).name(), "ACTIVE".equals(c.status), c.type, c.status, c.validFrom, c.validTo, id);
        saveProfile(id, b, c);
        audit.record("USER_UPDATED", "USER", id, target, c.fullName + " updated");
        return detail(id);
    }

    public UserDetail setStatus(Long id, String status) {
        requireId(id);
        String s = status == null ? "" : status.toUpperCase();
        if (!STATUSES.contains(s)) throw new FieldErrorsException(Map.of("status", "Choose Active, Inactive or Suspended."));
        if (id.equals(current.principal().userId()) && !"ACTIVE".equals(s)) throw ApiException.conflict("You can't deactivate your own account.");
        jdbc.update("update users set status = ?, active = ? where id = ?", s, "ACTIVE".equals(s), id);
        UserDetail d = detail(id);
        audit.record("USER_STATUS_CHANGED", "USER", id, d.institutionId(), d.fullName() + " set to " + s.toLowerCase());
        return d;
    }

    public ResetResult resetPassword(Long id, String custom) {
        requireId(id);
        UserDetail d = detail(id);
        Long inst = d.institutionId();
        String pw = custom == null || custom.isBlank() ? policy.generate(inst) : custom;
        if (custom != null && !custom.isBlank()) {
            String bad = policy.violation(inst, custom);
            if (bad != null) throw new FieldErrorsException(Map.of("password", bad));
        }
        jdbc.update("update users set password_hash = ? where id = ?", encoder.encode(pw), id);
        jdbc.update("update user_profiles set must_change_password = true, password_changed_at = now() where user_id = ?", id);
        audit.record("USER_PASSWORD_RESET", "USER", id, inst, "Password reset for " + d.fullName());
        return new ResetResult(custom == null || custom.isBlank() ? pw : null);
    }

    // ------------------------------------------------------------------ internals

    private record Clean(String type, String fullName, String email, String status, LocalDate validFrom, LocalDate validTo, String firstName, String lastName, String phoneCode, String phone,
                         LocalDate dob, LocalDate joinedOn, String gender, List<String> roles) {}

    private Clean clean(UserBody b, Long selfId, Long inst) {
        Map<String, String> err = new LinkedHashMap<>();
        String type = up(b.userType());
        if (!RoleCatalog.USER_TYPES.contains(type)) err.put("userType", "Choose a user type.");
        String first = trim(b.firstName()), last = trim(b.lastName());
        if (first.isEmpty()) err.put("firstName", "Required."); else if (first.length() > 100) err.put("firstName", "Use 100 characters or fewer.");
        if (last.isEmpty()) err.put("lastName", "Required."); else if (last.length() > 100) err.put("lastName", "Use 100 characters or fewer.");
        String email = trim(b.email()).toLowerCase();
        if (email.isEmpty()) err.put("email", "Required.");
        else if (email.length() > 200 || !EMAIL.matcher(email).matches()) err.put("email", "Enter a valid email address.");
        else if (jdbc.queryForObject("select count(*) from users where lower(email) = ? and id <> ?", Integer.class, email, selfId == null ? -1L : selfId) > 0) err.put("email", "This email is already in use.");
        String code = trim(b.phoneCode()).isEmpty() ? "+91" : trim(b.phoneCode());
        if (!code.matches("\\+\\d{1,4}")) err.put("phoneCode", "Use a code like +91.");
        String phone = trim(b.phone()).replace(" ", "");
        if (phone.isEmpty()) err.put("phone", "Required."); else if (!phone.matches("\\d{6,15}")) err.put("phone", "Enter 6 to 15 digits.");
        String gender = up(b.gender());
        if (!gender.isEmpty() && !GENDERS.contains(gender)) err.put("gender", "Choose a valid gender.");
        String status = b.status() == null || b.status().isBlank() ? "ACTIVE" : up(b.status());
        if (!STATUSES.contains(status)) err.put("status", "Choose Active, Inactive or Suspended.");
        LocalDate dob = date(b.dateOfBirth(), "dateOfBirth", err), from = date(b.validFrom(), "validFrom", err), to = date(b.validTo(), "validTo", err), joined = date(b.joinedOn(), "joinedOn", err);
        if (dob != null && dob.isAfter(LocalDate.now())) err.put("dateOfBirth", "Date of birth can't be in the future.");
        if (from != null && to != null && to.isBefore(from)) err.put("validTo", "Must be on or after Valid From.");
        if (trim(b.notes()).length() > 500) err.put("notes", "Use 500 characters or fewer.");

        List<String> roles = new ArrayList<>();
        if (b.roles() != null) for (String r : b.roles()) { String c = up(r); if (!roles.contains(c)) roles.add(c); }
        if (roles.isEmpty()) err.put("roles", "Select at least one role.");
        else if (roles.stream().anyMatch(r -> RoleCatalog.get(r) == null)) err.put("roles", "One of the selected roles is not valid.");

        if (inst == null) err.put("institutionId", "Select an institution.");
        else if (jdbc.queryForObject("select count(*) from institutions where id = ? and lifecycle <> 'ARCHIVED'", Integer.class, inst) == 0) err.put("institutionId", "Choose an active institution.");
        else if (b.academicYearId() != null && jdbc.queryForObject("select count(*) from academic_years where id = ? and institution_id = ?", Integer.class, b.academicYearId(), inst) == 0) err.put("academicYearId", "Choose an academic year of this institution.");
        if (b.dataScope() != null && !b.dataScope().isBlank() && !SCOPES.contains(up(b.dataScope()))) err.put("dataScope", "Choose a valid data access option.");

        if (b.resetAfterDays() != null && (b.resetAfterDays() < 1 || b.resetAfterDays() > 730)) err.put("resetAfterDays", "Enter 1 to 730 days.");
        limit(b.employeeId(), 40, "employeeId", err); limit(b.department(), 80, "department", err); limit(b.designation(), 80, "designation", err);
        limit(b.address1(), 150, "address1", err); limit(b.address2(), 150, "address2", err); limit(b.city(), 80, "city", err); limit(b.state(), 80, "state", err); limit(b.pinCode(), 12, "pinCode", err);
        if (!trim(b.altEmail()).isEmpty() && !EMAIL.matcher(trim(b.altEmail())).matches()) err.put("altEmail", "Enter a valid email address.");
        if (!trim(b.emergencyContact()).isEmpty() && !trim(b.emergencyContact()).replace(" ", "").matches("\\+?\\d{6,15}")) err.put("emergencyContact", "Enter a valid phone number.");
        if (b.notifyEvents() != null && !EVENTS.containsAll(b.notifyEvents())) err.put("notifyEvents", "Unknown notification type.");
        if (b.notifyVia() != null && !VIA.containsAll(b.notifyVia())) err.put("notifyVia", "Unknown notification channel.");
        if (b.language() != null && !b.language().isBlank() && !LANGS.contains(b.language())) err.put("language", "Choose a supported language.");
        if (b.timezone() != null && !b.timezone().isBlank() && !ZoneId.getAvailableZoneIds().contains(b.timezone())) err.put("timezone", "Choose a valid time zone.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        return new Clean(type, first + " " + last, email, status, from, to, first, last, code, phone, dob, joined, gender.isEmpty() ? null : gender, roles);
    }

    private void saveProfile(Long id, UserBody b, Clean c) {
        String[] cols = {"first_name", "last_name", "phone_code", "phone", "gender", "date_of_birth", "notes", "must_change_password", "two_factor", "reset_after_days", "employee_id", "department", "designation",
                "joined_on", "alt_email", "emergency_contact", "address1", "address2", "city", "state", "pin_code", "data_scope", "academic_year_id", "notify_events", "notify_language", "notify_timezone", "notify_via"};
        Object[] vals = {c.firstName, c.lastName, c.phoneCode, c.phone, c.gender, c.dob, blank(b.notes()), b.mustChangePassword() == null || b.mustChangePassword(), Boolean.TRUE.equals(b.twoFactor()), b.resetAfterDays(),
                blank(b.employeeId()), blank(b.department()), blank(b.designation()), c.joinedOn, blank(b.altEmail()), blank(b.emergencyContact()), blank(b.address1()), blank(b.address2()), blank(b.city()), blank(b.state()),
                blank(b.pinCode()), b.dataScope() == null || b.dataScope().isBlank() ? "ASSIGNED" : up(b.dataScope()), b.academicYearId(),
                b.notifyEvents() == null ? "WELCOME_EMAIL,PASSWORD_RESET,ANNOUNCEMENTS,TIMETABLE,EXAMS,FEES,LIBRARY" : String.join(",", b.notifyEvents()),
                b.language() == null || b.language().isBlank() ? "en" : b.language(), b.timezone() == null || b.timezone().isBlank() ? "Asia/Kolkata" : b.timezone(),
                b.notifyVia() == null ? "EMAIL,IN_APP" : String.join(",", b.notifyVia())};
        StringBuilder names = new StringBuilder(), marks = new StringBuilder(), sets = new StringBuilder();
        for (String col : cols) { names.append(", ").append(col); marks.append(", ?"); sets.append(sets.length() == 0 ? "" : ", ").append(col).append(" = excluded.").append(col); }
        Object[] args = new Object[vals.length + 1];
        args[0] = id;
        System.arraycopy(vals, 0, args, 1, vals.length);
        jdbc.update("insert into user_profiles (user_id" + names + ") values (?" + marks + ") on conflict (user_id) do update set " + sets, args);
        jdbc.update("delete from user_roles where user_id = ?", id);
        for (int i = 0; i < c.roles.size(); i++) jdbc.update("insert into user_roles (user_id, role_code, is_primary) values (?, ?, ?)", id, c.roles.get(i), i == 0);
    }

    private UserDetail detail(Long id) {
        List<Object> args = new ArrayList<>();
        String scope = scopeSql(null, args);
        args.add(id);
        List<UserDetail> list = jdbc.query("select u.id, u.created_at, u.user_type, p.first_name, p.last_name, u.full_name, u.email, p.phone_code, p.phone, p.date_of_birth, p.gender, u.status, u.valid_from, u.valid_to, p.notes, "
                + "p.must_change_password, p.two_factor, p.reset_after_days, " + ROLES_SQL + ", u.institution_id, i.name, i.institution_type, p.data_scope, p.academic_year_id, y.name, p.employee_id, p.department, p.designation, p.joined_on, "
                + "p.alt_email, p.emergency_contact, p.address1, p.address2, p.city, p.state, p.pin_code, p.notify_events, p.notify_language, p.notify_timezone, p.notify_via, u.last_login_at"
                + FROM + scope + " and u.id = ?", (rs, n) -> mapDetail(rs), args.toArray());
        if (list.isEmpty()) throw ApiException.notFound("User");
        return list.get(0);
    }

    private UserDetail mapDetail(ResultSet rs) throws SQLException {
        Long instId = (Long) rs.getObject(20);
        return new UserDetail(rs.getLong(1), "USR-" + rs.getTimestamp(2).toLocalDateTime().getYear() + "-" + (1000 + rs.getLong(1)), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getString(9), d(rs, 10), rs.getString(11), rs.getString(12), d(rs, 13), d(rs, 14), rs.getString(15), rs.getBoolean(16), rs.getBoolean(17), (Integer) rs.getObject(18),
                codes(rs.getString(19)), instId, rs.getString(21), instId == null ? null : String.format("INS-%04d", instId), rs.getString(22), rs.getString(23), (Long) rs.getObject(24), rs.getString(25),
                rs.getString(26), rs.getString(27), rs.getString(28), d(rs, 29), rs.getString(30), rs.getString(31), rs.getString(32), rs.getString(33), rs.getString(34), rs.getString(35), rs.getString(36),
                csv(rs.getString(37)), rs.getString(38), rs.getString(39), csv(rs.getString(40)), rs.getTimestamp(2).toInstant().toString(), ts(rs, 41));
    }

    private Long requireId(Long id) {
        List<Object> args = new ArrayList<>();
        String scope = scopeSql(null, args);
        args.add(id);
        if (jdbc.queryForObject("select count(*)" + FROM + scope + " and u.id = ?", Integer.class, args.toArray()) == 0) throw ApiException.notFound("User");
        return id;
    }

    /** Restricts to the signed-in admin's own institution; Super Admin may filter by an institution. */
    private String scopeSql(Long institutionId, List<Object> args) {
        Long inst = locked() != null ? locked() : institutionId;
        if (inst == null) return "";
        args.add(inst);
        return " and u.institution_id = ?";
    }

    private static List<String> codes(String csv) { return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(",")); }
    private static List<String> csv(String v) { return codes(v); }
    private static String ts(ResultSet rs, int i) throws SQLException { var t = rs.getTimestamp(i); return t == null ? null : t.toInstant().toString(); }
    private static String d(ResultSet rs, int i) throws SQLException { Date v = rs.getDate(i); return v == null ? null : v.toLocalDate().toString(); }
    private static String trim(String v) { return v == null ? "" : v.trim(); }
    private static String up(String v) { return trim(v).toUpperCase(); }
    private static String blank(String v) { return trim(v).isEmpty() ? null : trim(v); }
    private static void limit(String v, int max, String key, Map<String, String> err) { if (trim(v).length() > max) err.put(key, "Use " + max + " characters or fewer."); }
    private static LocalDate date(String v, String key, Map<String, String> err) {
        if (v == null || v.isBlank()) return null;
        try { return LocalDate.parse(v.trim()); } catch (java.time.format.DateTimeParseException e) { err.put(key, "Enter a valid date."); return null; }
    }
}
