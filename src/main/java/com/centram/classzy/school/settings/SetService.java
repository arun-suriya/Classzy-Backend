package com.centram.classzy.school.settings;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.settings.SetDto.*;
import com.centram.classzy.usermgmt.RoleCatalog;
import com.centram.classzy.usermgmt.UserMgmtDto;
import com.centram.classzy.usermgmt.UserMgmtService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings for one school. Most values are stored for the school's records; the notes next to each page say which ones the rest of Classzy actually uses today.
 * Integrations only remember which provider was chosen: nothing connects to a gateway, messaging service or biometric device.
 */
@Service
@Transactional
public class SetService {

    static final List<String> LANGUAGES = List.of("English", "Hindi", "Tamil", "Telugu", "Kannada", "Malayalam", "Marathi", "Bengali");
    static final List<String> ZONES = List.of("(GMT+05:30) India Standard Time", "(GMT+04:00) Gulf Standard Time", "(GMT+00:00) Greenwich Mean Time", "(GMT-05:00) Eastern Time", "(GMT+08:00) Singapore Time");
    static final List<String> DATE_FORMATS = List.of("DD MMM YYYY", "DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD");
    static final List<Code> GRADING = List.of(new Code("PERCENTAGE", "Percentage (0 – 100)"), new Code("GPA_10", "GPA (0 – 10)"), new Code("GPA_4", "GPA (0 – 4)"), new Code("LETTER", "Letter grades (A – F)"));
    static final List<String> DOCUMENTS = List.of("Birth Certificate", "Previous School Report", "ID Proof", "Address Proof", "Passport Photo", "Transfer Certificate", "Medical Certificate", "Parent ID Proof");
    static final List<Code> WORKFLOWS = List.of(new Code("STANDARD", "Application → Document Verification → Approval"), new Code("DIRECT", "Application → Approval"), new Code("INTERVIEW", "Application → Interview → Approval"));
    static final List<String> CURRENCIES = List.of("INR (₹)", "USD ($)", "EUR (€)", "GBP (£)", "AED (د.إ)");
    static final List<Code> BILLING = List.of(new Code("MONTHLY", "Monthly"), new Code("TERM", "Term-wise"), new Code("QUARTERLY", "Quarterly"), new Code("ANNUAL", "Annual"));
    static final List<Code> DUE_RULES = List.of(new Code("FIRST_15", "On or before 15th of the first month of the term"), new Code("FIRST_10", "On or before 10th of the first month of the term"), new Code("TERM_START", "On the first day of the term"), new Code("DAYS_30", "30 days after the invoice date"));
    static final List<Code> LATE_RULES = List.of(new Code("NONE", "No late fee"), new Code("PER_DAY", "Amount per day after due date"), new Code("FLAT", "One-time amount after due date"));
    private static final List<Code> ROLE_LIST = RoleCatalog.all().stream().map((r) -> new Code(r.code(), r.name())).toList();
    private static final Map<String, String> SERVICES = new LinkedHashMap<>();
    private static final Map<String, List<String>> PROVIDERS = Map.of(
            "PAYMENT", List.of("Razorpay", "PayU", "Cashfree", "Stripe"),
            "MESSAGING", List.of("Email (SMTP)", "SMS", "WhatsApp"),
            "BIOMETRIC", List.of("ZKTeco", "eSSL", "Realtime", "Other"));
    static { SERVICES.put("PAYMENT", "Payment Gateway"); SERVICES.put("MESSAGING", "Messaging"); SERVICES.put("BIOMETRIC", "Biometric Attendance"); }
    private static final Pattern PREFIX = Pattern.compile("^[A-Za-z0-9/_-]{1,20}$");

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;
    private final UserMgmtService users;

    public SetService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, UserMgmtService users) { this.jdbc = jdbc; this.current = current; this.audit = audit; this.users = users; }

    private long inst() { return current.institutionId(); }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }
    private static boolean in(List<Code> l, String c) { return c != null && l.stream().anyMatch((x) -> x.code().equals(c)); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static String day(Date d) { return d == null ? null : d.toLocalDate().toString(); }
    private void ensureRow() { jdbc.update("insert into school_settings (institution_id) values (?) on conflict do nothing", inst()); }

    // ------------------------------------------------------------------ read

    public All all() {
        long i = inst();
        ensureRow();
        General g = jdbc.queryForObject("select i.name, (select y.id from academic_years y where y.institution_id = i.id and y.is_current limit 1), i.language, i.time_zone, i.date_format, exists (select 1 from institution_images m where m.institution_id = i.id and m.kind = 'LOGO') from institutions i where i.id = ?",
                (rs, n) -> new General(rs.getString(1), (Long) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6)), i);
        Academic a = jdbc.queryForObject("select coalesce(nullif(i.grading_system, ''), 'PERCENTAGE'), coalesce((select t.default_duration from timetable_settings t where t.institution_id = i.id), 45), s.attendance_per_period, s.late_marking_allowed, s.late_marking_minutes from institutions i join school_settings s on s.institution_id = i.id where i.id = ?",
                (rs, n) -> new Academic(rs.getString(1), rs.getInt(2), rs.getBoolean(3), rs.getBoolean(4), rs.getInt(5), termsSummary(i), gradesSummary(i)), i);
        Admissions d = jdbc.queryForObject("select admission_year_id, application_opens, application_closes, required_documents, approval_workflow from school_settings where institution_id = ?",
                (rs, n) -> new Admissions((Long) rs.getObject(1), day(rs.getDate(2)), day(rs.getDate(3)), split(rs.getString(4)), rs.getString(5)), i);
        Fees f = jdbc.queryForObject("select i.currency, s.billing_frequency, s.due_date_rule, s.late_fee_rule, s.late_fee_amount, s.receipt_prefix from institutions i join school_settings s on s.institution_id = i.id where i.id = ?",
                (rs, n) -> new Fees(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5).doubleValue(), rs.getString(6)), i);
        Map<String, String> chosen = new LinkedHashMap<>();
        jdbc.query("select service, provider from school_integrations where institution_id = ?", (rs) -> { chosen.put(rs.getString(1), rs.getString(2)); }, i);
        List<Integration> ints = SERVICES.entrySet().stream().map((e) -> new Integration(e.getKey(), e.getValue(), chosen.get(e.getKey()), chosen.containsKey(e.getKey()) ? "SELECTED" : "NOT_CONFIGURED", PROVIDERS.get(e.getKey()))).toList();
        List<YearOpt> years = jdbc.query("select id, name, is_current from academic_years where institution_id = ? order by start_date desc", (rs, n) -> new YearOpt(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), i);
        List<String> zones = new ArrayList<>(ZONES); if (g.timeZone() != null && !zones.contains(g.timeZone())) zones.add(0, g.timeZone());
        List<String> formats = new ArrayList<>(DATE_FORMATS); if (g.dateFormat() != null && !formats.contains(g.dateFormat())) formats.add(0, g.dateFormat());
        List<String> langs = new ArrayList<>(LANGUAGES); if (g.language() != null && !langs.contains(g.language())) langs.add(0, g.language());
        List<String> cur = new ArrayList<>(CURRENCIES); if (f.currency() != null && !cur.contains(f.currency())) cur.add(0, f.currency());
        List<String> docs = new ArrayList<>(DOCUMENTS); d.documents().stream().filter((x) -> !docs.contains(x)).forEach(docs::add);
        return new All(g, a, d, f, ints, new Options(years, langs, zones, formats, GRADING, docs, WORKFLOWS, cur, BILLING, DUE_RULES, LATE_RULES, ROLE_LIST));
    }

    private static List<String> split(String s) { return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).filter((x) -> !x.isEmpty()).toList(); }

    private String termsSummary(long i) {
        List<String> t = jdbc.query("select t.name || ' (' || to_char(t.start_date, 'Mon') || ' – ' || to_char(t.end_date, 'Mon') || ')' from terms t join academic_years y on y.id = t.academic_year_id where t.institution_id = ? and y.is_current and t.active order by t.start_date", (rs, n) -> rs.getString(1), i);
        return t.isEmpty() ? "No terms set up for the current year" : String.join(", ", t);
    }

    private String gradesSummary(long i) {
        List<String> g = jdbc.query("select c.name from school_classes c join academic_years y on y.id = c.academic_year_id where c.institution_id = ? and y.is_current and c.active order by coalesce(c.grade_level, -1), c.name", (rs, n) -> rs.getString(1), i);
        return g.isEmpty() ? "No grades set up for the current year" : g.size() == 1 ? g.get(0) : g.get(0) + " – " + g.get(g.size() - 1) + " (" + g.size() + " grades)";
    }

    // ------------------------------------------------------------------ write

    public void saveGeneral(GeneralBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim();
        if (name.isEmpty()) e.put("name", "Required."); else if (name.length() > 200) e.put("name", "Use 200 characters or fewer.");
        else if (count("select count(*) from institutions where lower(name) = lower(?) and id <> ?", name, i) > 0) e.put("name", "Another institution already uses this name.");
        All cur = all();
        if (b.language() == null || !cur.options().languages().contains(b.language())) e.put("language", "Choose a language.");
        if (b.timeZone() == null || !cur.options().timeZones().contains(b.timeZone())) e.put("timeZone", "Choose a time zone.");
        if (b.dateFormat() == null || !cur.options().dateFormats().contains(b.dateFormat())) e.put("dateFormat", "Choose a date format.");
        if (b.academicYearId() != null && count("select count(*) from academic_years where id = ? and institution_id = ?", b.academicYearId(), i) == 0) e.put("academicYearId", "Choose an academic year of this school.");
        fail(e);
        jdbc.update("update institutions set name = ?, language = ?, time_zone = ?, date_format = ?, updated_at = now() where id = ?", name, b.language(), b.timeZone(), b.dateFormat(), i);
        if (b.academicYearId() != null && !b.academicYearId().equals(cur.general().academicYearId())) {
            jdbc.update("update academic_years set is_current = false where institution_id = ? and is_current", i);
            jdbc.update("update academic_years set is_current = true where id = ?", b.academicYearId());
        }
        audit.record("SCHOOL_SETTINGS_GENERAL", "SETTINGS", i, i, "General settings updated");
    }

    public void saveAcademic(AcademicBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        if (!in(GRADING, b.gradingScale())) e.put("gradingScale", "Choose a grading scale.");
        if (b.periodDuration() == null || b.periodDuration() < 10 || b.periodDuration() > 180) e.put("periodDuration", "Enter 10 to 180 minutes.");
        if (b.lateMarkingMinutes() == null || b.lateMarkingMinutes() < 1 || b.lateMarkingMinutes() > 120) e.put("lateMarkingMinutes", "Enter 1 to 120 minutes.");
        fail(e);
        ensureRow();
        jdbc.update("update institutions set grading_system = ?, updated_at = now() where id = ?", b.gradingScale(), i);
        jdbc.update("insert into timetable_settings (institution_id, default_duration) values (?, ?) on conflict (institution_id) do update set default_duration = excluded.default_duration", i, b.periodDuration());
        jdbc.update("update school_settings set attendance_per_period = ?, late_marking_allowed = ?, late_marking_minutes = ?, updated_at = now() where institution_id = ?", !Boolean.FALSE.equals(b.attendancePerPeriod()), !Boolean.FALSE.equals(b.lateMarkingAllowed()), b.lateMarkingMinutes(), i);
        audit.record("SCHOOL_SETTINGS_ACADEMIC", "SETTINGS", i, i, "Academic settings updated");
    }

    public void saveAdmissions(AdmissionsBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        if (b.yearId() != null && count("select count(*) from academic_years where id = ? and institution_id = ?", b.yearId(), i) == 0) e.put("yearId", "Choose an academic year of this school.");
        LocalDate o = null, c = null;
        try { if (b.opens() != null && !b.opens().isBlank()) o = LocalDate.parse(b.opens().trim()); } catch (DateTimeParseException x) { e.put("opens", "Choose a valid date."); }
        try { if (b.closes() != null && !b.closes().isBlank()) c = LocalDate.parse(b.closes().trim()); } catch (DateTimeParseException x) { e.put("closes", "Choose a valid date."); }
        if (o != null && c != null && c.isBefore(o)) e.put("closes", "Applications can't close before they open.");
        List<String> docs = b.documents() == null ? List.of() : b.documents().stream().map((x) -> x == null ? "" : x.trim()).filter((x) -> !x.isEmpty()).distinct().toList();
        if (docs.size() > 12) e.put("documents", "Choose 12 documents or fewer.");
        else if (docs.stream().anyMatch((x) -> x.length() > 40 || x.contains(","))) e.put("documents", "Document names must be 40 characters or fewer and can't contain commas.");
        if (!in(WORKFLOWS, b.workflow())) e.put("workflow", "Choose an approval workflow.");
        fail(e);
        ensureRow();
        jdbc.update("update school_settings set admission_year_id = ?, application_opens = ?, application_closes = ?, required_documents = ?, approval_workflow = ?, updated_at = now() where institution_id = ?",
                b.yearId(), o == null ? null : Date.valueOf(o), c == null ? null : Date.valueOf(c), String.join(",", docs), b.workflow(), i);
        audit.record("SCHOOL_SETTINGS_ADMISSIONS", "SETTINGS", i, i, "Admission settings updated");
    }

    public void saveFees(FeesBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        All cur = all();
        if (b.currency() == null || !cur.options().currencies().contains(b.currency())) e.put("currency", "Choose a currency.");
        if (!in(BILLING, b.billingFrequency())) e.put("billingFrequency", "Choose a billing frequency.");
        if (!in(DUE_RULES, b.dueDateRule())) e.put("dueDateRule", "Choose a due date rule.");
        if (!in(LATE_RULES, b.lateFeeRule())) e.put("lateFeeRule", "Choose a late fee rule.");
        double amt = b.lateFeeAmount() == null ? 0 : b.lateFeeAmount();
        if (Double.isNaN(amt) || amt < 0 || amt > 100000) e.put("lateFeeAmount", "Enter an amount between 0 and 100000.");
        else if (!"NONE".equals(b.lateFeeRule()) && in(LATE_RULES, b.lateFeeRule()) && amt <= 0) e.put("lateFeeAmount", "Enter the late fee amount.");
        String prefix = b.receiptPrefix() == null ? "" : b.receiptPrefix().trim();
        if (!PREFIX.matcher(prefix).matches()) e.put("receiptPrefix", "Use 1 to 20 letters, numbers, / - or _.");
        fail(e);
        ensureRow();
        jdbc.update("update institutions set currency = ?, updated_at = now() where id = ?", b.currency(), i);
        jdbc.update("update school_settings set billing_frequency = ?, due_date_rule = ?, late_fee_rule = ?, late_fee_amount = ?, receipt_prefix = ?, updated_at = now() where institution_id = ?",
                b.billingFrequency(), b.dueDateRule(), b.lateFeeRule(), "NONE".equals(b.lateFeeRule()) ? BigDecimal.ZERO : BigDecimal.valueOf(amt).setScale(2, java.math.RoundingMode.HALF_UP), prefix, i);
        audit.record("SCHOOL_SETTINGS_FEES", "SETTINGS", i, i, "Fee settings updated");
    }

    public void saveIntegration(String service, String provider) {
        long i = inst();
        String s = service == null ? "" : service.toUpperCase();
        if (!SERVICES.containsKey(s)) throw ApiException.notFound("Service");
        if (provider == null || provider.isBlank()) {
            jdbc.update("delete from school_integrations where institution_id = ? and service = ?", i, s);
            audit.record("SCHOOL_INTEGRATION", "SETTINGS", i, i, SERVICES.get(s) + " provider cleared");
            return;
        }
        if (!PROVIDERS.get(s).contains(provider)) throw new FieldErrorsException(Map.of("provider", "Choose a provider from the list."));
        jdbc.update("insert into school_integrations (institution_id, service, provider) values (?,?,?) on conflict (institution_id, service) do update set provider = excluded.provider, updated_at = now()", i, s, provider);
        audit.record("SCHOOL_INTEGRATION", "SETTINGS", i, i, SERVICES.get(s) + " provider set to " + provider);
    }

    // ------------------------------------------------------------------ users

    private static boolean signsIn(String type) { return type.equals("ADMINISTRATOR") || type.equals("TEACHER"); }
    /** Sign-in access follows the account type, so the role decides which type the account gets. */
    static String typeFor(String role) {
        return switch (role) { case "ADMINISTRATOR" -> "ADMINISTRATOR"; case "TEACHER", "CLASS_TEACHER", "SUBJECT_TEACHER" -> "TEACHER"; case "STUDENT" -> "STUDENT"; case "PARENT" -> "PARENT"; default -> "STAFF"; };
    }

    private SchoolUser userRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String type = rs.getString(4);
        return new SchoolUser(rs.getLong(1), rs.getString(2), rs.getString(3), type, rs.getString(5), rs.getString(6), signsIn(type), rs.getLong(1) == current.principal().userId());
    }
    private static final String USER_COLS = "select u.id, u.full_name, u.email, u.user_type, coalesce((select r.role_code from user_roles r where r.user_id = u.id order by r.is_primary desc, r.role_code limit 1), 'ADMINISTRATOR'), u.status from users u";

    @Transactional(readOnly = true)
    public List<SchoolUser> users(String q, String role) {
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where u.institution_id = ?");
        if (q != null && !q.isBlank()) { String l = "%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%"; w.append(" and (lower(u.full_name) like ? or lower(u.email) like ?)"); a.add(l); a.add(l); }
        if (role != null && !role.isBlank()) { w.append(" and exists (select 1 from user_roles r where r.user_id = u.id and r.role_code = ?)"); a.add(role.toUpperCase()); }
        return jdbc.query(USER_COLS + w + " order by u.created_at, u.id limit 200", (rs, n) -> userRow(rs), a.toArray());
    }

    private SchoolUser one(Long id) { return jdbc.query(USER_COLS + " where u.id = ? and u.institution_id = ?", (rs, n) -> userRow(rs), id, inst()).stream().findFirst().orElseThrow(() -> ApiException.notFound("User")); }

    public Invited invite(InviteBody b) {
        String role = b.role() == null ? "" : b.role().toUpperCase();
        if (RoleCatalog.get(role) == null) throw new FieldErrorsException(Map.of("role", "Choose a role."));
        String type = typeFor(role);
        var created = users.create(new UserMgmtDto.UserBody(type, b.firstName(), b.lastName(), b.email(), "+91", b.phone(), null, null, "ACTIVE", null, null, null, "AUTO", null, true, false, null,
                List.of(role), inst(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        return new Invited(one(created.user().id()), created.temporaryPassword(), signsIn(type));
    }

    public SchoolUser changeRole(Long id, String roleIn) {
        long i = inst();
        String role = roleIn == null ? "" : roleIn.toUpperCase();
        if (RoleCatalog.get(role) == null) throw new FieldErrorsException(Map.of("role", "Choose a role."));
        SchoolUser u = one(id);
        if (u.self()) throw ApiException.conflict("You can't change your own role.");
        String type = typeFor(role);
        if (u.userType().equals("ADMINISTRATOR") && !type.equals("ADMINISTRATOR") && u.status().equals("ACTIVE")
                && count("select count(*) from users where institution_id = ? and user_type = 'ADMINISTRATOR' and status = 'ACTIVE' and id <> ?", i, id) == 0) throw ApiException.conflict("This is the school's last active administrator.");
        jdbc.update("update users set user_type = ?, role = ? where id = ?", type, com.centram.classzy.usermgmt.RoleCatalog.loginRole(type).name(), id);
        jdbc.update("delete from user_roles where user_id = ?", id);
        jdbc.update("insert into user_roles (user_id, role_code, is_primary) values (?, ?, true)", id, role);
        audit.record("USER_ROLE_CHANGED", "USER", id, i, u.fullName() + " is now " + RoleCatalog.get(role).name());
        return one(id);
    }

    public SchoolUser setStatus(Long id, String status) {
        long i = inst();
        SchoolUser u = one(id);
        String s = status == null ? "" : status.toUpperCase();
        if (!s.equals("ACTIVE") && !s.equals("INACTIVE")) throw new FieldErrorsException(Map.of("status", "Choose Active or Inactive."));
        if (u.self()) throw ApiException.conflict("You can't deactivate your own account.");
        if (s.equals("INACTIVE") && u.userType().equals("ADMINISTRATOR") && u.status().equals("ACTIVE")
                && count("select count(*) from users where institution_id = ? and user_type = 'ADMINISTRATOR' and status = 'ACTIVE' and id <> ?", i, id) == 0) throw ApiException.conflict("This is the school's last active administrator.");
        users.setStatus(id, s);
        return one(id);
    }

    public String resetPassword(Long id) {
        one(id);
        return users.resetPassword(id, null).temporaryPassword();
    }
}
