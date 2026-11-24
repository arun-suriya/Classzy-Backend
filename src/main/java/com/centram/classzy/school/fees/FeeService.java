package com.centram.classzy.school.fees;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.admissions.AdmDto.ClassOpt;
import com.centram.classzy.school.admissions.AdmDto.Year;
import com.centram.classzy.school.fees.FeeDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fees & Finance for one school. Invoices are school_fee_records; their running figures come from the school_invoice_v view:
 * net = amount - approved concessions - scholarship allocations, paid = payments - refunds marked refunded, balance = net - paid.
 * Payments, refunds and receipts are records only: no payment gateway, nothing is emailed or sent to parents.
 */
@Service
@Transactional
public class FeeService {

    static final List<Code> FREQUENCIES = List.of(new Code("TERM", "Term"), new Code("YEARLY", "Yearly"), new Code("ONE_TIME", "One-time"));
    static final List<Code> MODES = List.of(new Code("CASH", "Cash"), new Code("UPI", "UPI"), new Code("CARD", "Card"), new Code("BANK", "Bank transfer"), new Code("CHEQUE", "Cheque"));
    static final Set<String> STRUCT_FREQ = Set.of("TERM", "YEARLY", "ONE_TIME");
    static final BigDecimal MAX = new BigDecimal("100000000");

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    private final com.centram.classzy.school.comm.NotifyService notify;

    public FeeService(JdbcTemplate jdbc, CurrentUser current, AuditService audit, com.centram.classzy.school.comm.NotifyService notify) {
        this.notify = notify;
        this.jdbc = jdbc;
        this.current = current;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ helpers

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }
    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }
    private static String day(Date d) { return d == null ? null : d.toLocalDate().toString(); }
    private static double d(BigDecimal b) { return b == null ? 0 : b.doubleValue(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static String clip(String v, int max) { if (v == null) return null; String t = v.trim(); return t.isEmpty() ? null : (t.length() > max ? t.substring(0, max) : t); }
    private static String like(String q) { return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
    private static BigDecimal money(Double v, String key, Map<String, String> e) {
        if (v == null || v.isNaN() || v.isInfinite() || v <= 0) { e.put(key, "Enter an amount above 0."); return null; }
        BigDecimal m = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
        if (m.signum() <= 0 || m.compareTo(MAX) > 0) { e.put(key, "Enter an amount between 0.01 and 10,00,00,000."); return null; }
        return m;
    }
    private static LocalDate date(String v, String key, boolean required, Map<String, String> e) {
        if (v == null || v.isBlank()) { if (required) e.put(key, "Required."); return null; }
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException x) { e.put(key, "Use a date like 2026-10-12."); return null; }
    }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }
    private void lockNumbers() { jdbc.queryForObject("select pg_advisory_xact_lock(hashtext('fee-' || ?))::text", String.class, String.valueOf(inst())); }
    private long nextNumber(String table, String col, String prefix) {
        return count("select coalesce(max(substring(" + col + " from " + (prefix.length() + 2) + ")::bigint), 0) from " + table + " where institution_id = ? and " + col + " ~ ?", inst(), "^" + prefix + "-[0-9]+$") + 1;
    }
    private static String no(String prefix, long n) { return prefix + "-" + String.format("%04d", n); }

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        List<Year> years = jdbc.query("select id, name, is_current from academic_years where institution_id = ? and active order by start_date desc", (rs, n) -> new Year(rs.getLong(1), rs.getString(2), rs.getBoolean(3)), i);
        List<TermOpt> terms = jdbc.query("select id, academic_year_id, name, start_date, end_date from terms where institution_id = ? and active order by start_date", (rs, n) -> new TermOpt(rs.getLong(1), rs.getLong(2), rs.getString(3), day(rs.getDate(4)), day(rs.getDate(5))), i);
        List<ClassOpt> classes = jdbc.query("select id, name, academic_year_id from school_classes where institution_id = ? and active order by coalesce(grade_level, -1), name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getString(2), rs.getLong(3)), i);
        return new Options(years, terms, classes, FREQUENCIES, MODES);
    }

    @Transactional(readOnly = true)
    public List<StudentHit> students(String q) {
        List<Object> a = new ArrayList<>(List.of(inst()));
        String w = "";
        if (q != null && !q.isBlank()) { w = " and (lower(s.full_name) like lower(?) escape '\\' or lower(s.admission_no) like lower(?) escape '\\')"; a.add(like(q)); a.add(like(q)); }
        return jdbc.query("select s.id, s.admission_no, s.full_name, c.name, sec.name from students s left join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id where s.institution_id = ? and s.active" + w + " order by s.full_name limit 15",
                (rs, n) -> new StudentHit(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), a.toArray());
    }

    // ------------------------------------------------------------------ invoices (Fee Collection)

    private static final String STATUS_SQL = "case when v.balance <= 0 then 'PAID' when v.due_date < current_date then 'OVERDUE' when v.paid > 0 then 'PARTIAL' else 'PENDING' end";
    private static final String INV_COLS = "select v.id, v.invoice_no, s.id, s.full_name, s.admission_no, c.name, v.fee_type, t.name, v.amount, v.net, v.paid, v.balance, v.due_date, " + STATUS_SQL;
    private static final String INV_FROM = " from school_invoice_v v join students s on s.id = v.student_id left join school_classes c on c.id = s.school_class_id left join terms t on t.id = v.term_id";
    private static InvoiceRow inv(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InvoiceRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), d(rs.getBigDecimal(9)), d(rs.getBigDecimal(10)), d(rs.getBigDecimal(11)), d(rs.getBigDecimal(12)), day(rs.getDate(13)), rs.getString(14));
    }

    @Transactional(readOnly = true)
    public InvoicePage invoices(Long yearId, Long termId, Long classId, String status, String q, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where v.institution_id = ?");
        if (yearId != null) { w.append(" and v.academic_year_id = ?"); a.add(yearId); }
        if (termId != null) { w.append(" and (v.term_id = ? or v.term_id is null)"); a.add(termId); }
        if (classId != null) { w.append(" and s.school_class_id = ?"); a.add(classId); }
        if (q != null && !q.isBlank()) { w.append(" and (lower(s.full_name) like lower(?) escape '\\' or lower(s.admission_no) like lower(?) escape '\\' or lower(v.invoice_no) like lower(?) escape '\\')"); String l = like(q); a.add(l); a.add(l); a.add(l); }
        String scope = w.toString();
        Object[] t = jdbc.query("select coalesce(sum(v.net), 0), coalesce(sum(v.paid), 0), coalesce(sum(greatest(v.balance, 0)), 0)" + INV_FROM + scope, (rs, n) -> new Object[] { rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3) }, a.toArray()).get(0);
        if (status != null && !status.isBlank()) { w.append(" and ").append(STATUS_SQL).append(" = ?"); a.add(status.trim().toUpperCase()); }
        long total = count("select count(*)" + INV_FROM + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        List<InvoiceRow> items = jdbc.query(INV_COLS + INV_FROM + w + " order by v.due_date nulls last, s.full_name, v.id limit ? offset ?", (rs, n) -> inv(rs), a.toArray());
        return new InvoicePage(items, total, pg, sz, d((BigDecimal) t[0]), d((BigDecimal) t[1]), d((BigDecimal) t[2]));
    }

    @Transactional(readOnly = true)
    public List<InvoiceRow> openInvoices(Long studentId) {
        return jdbc.query(INV_COLS + INV_FROM + " where v.institution_id = ? and v.student_id = ? and v.balance > 0 order by v.due_date nulls last, v.id", (rs, n) -> inv(rs), inst(), studentId);
    }

    @Transactional(readOnly = true)
    public List<StudentPay> studentPayments(Long studentId) {
        return jdbc.query("select p.id, p.receipt_no, r.invoice_no, r.fee_type, p.paid_on, p.amount, p.amount - coalesce((select sum(f.amount) from school_fee_refunds f where f.payment_id = p.id and f.status in ('PENDING','REFUNDED')), 0)"
                + " from school_fee_payments p join school_fee_records r on r.id = p.fee_record_id where p.institution_id = ? and r.student_id = ? order by p.paid_on desc, p.id desc",
                (rs, n) -> new StudentPay(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), day(rs.getDate(5)), d(rs.getBigDecimal(6)), d(rs.getBigDecimal(7))), inst(), studentId);
    }

    @Transactional(readOnly = true)
    public InvoiceDetail invoice(Long id) {
        long i = inst();
        Object[] x = jdbc.query(INV_COLS + ", v.concession, v.scholarship" + INV_FROM + " where v.institution_id = ? and v.id = ?", (rs, n) -> new Object[] { inv(rs), rs.getBigDecimal(15), rs.getBigDecimal(16) }, i, id).stream().findFirst().orElseThrow(() -> ApiException.notFound("Invoice"));
        List<PayRow> pays = jdbc.query("select p.id, p.receipt_no, p.amount, p.paid_on, p.method, p.reference_no, p.notes, p.amount - coalesce((select sum(f.amount) from school_fee_refunds f where f.payment_id = p.id and f.status in ('PENDING','REFUNDED')), 0) from school_fee_payments p where p.fee_record_id = ? order by p.paid_on, p.id",
                (rs, n) -> new PayRow(rs.getLong(1), rs.getString(2), d(rs.getBigDecimal(3)), day(rs.getDate(4)), rs.getString(5), rs.getString(6), rs.getString(7), d(rs.getBigDecimal(8))), id);
        return new InvoiceDetail((InvoiceRow) x[0], d((BigDecimal) x[1]), d((BigDecimal) x[2]), pays);
    }

    public Receipt pay(PayBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        BigDecimal amount = money(b.amount(), "amount", e);
        LocalDate paidOn = date(b.paidOn(), "paidOn", true, e);
        if (paidOn != null && paidOn.isAfter(LocalDate.now())) e.put("paidOn", "The payment date can't be in the future.");
        String mode = b.mode() == null ? "" : b.mode().trim().toUpperCase();
        if (MODES.stream().noneMatch((m) -> m.code().equals(mode))) e.put("mode", "Choose a payment mode.");
        if (b.invoiceId() == null) e.put("invoiceId", "Choose an invoice.");
        fail(e);
        lockNumbers();
        BigDecimal balance = jdbc.query("select balance from school_invoice_v where id = ? and institution_id = ?", (rs, n) -> rs.getBigDecimal(1), b.invoiceId(), i).stream().findFirst().orElseThrow(() -> new FieldErrorsException(Map.of("invoiceId", "Choose an invoice of this school.")));
        if (balance.signum() <= 0) throw new FieldErrorsException(Map.of("invoiceId", "This invoice is already paid."));
        if (amount.compareTo(balance) > 0) throw new FieldErrorsException(Map.of("amount", "That's more than the balance of " + balance.toPlainString() + "."));
        String receipt = no("RCPT", nextNumber("school_fee_payments", "receipt_no", "RCPT"));
        Long id = jdbc.queryForObject("insert into school_fee_payments (institution_id, fee_record_id, amount, paid_on, method, receipt_no, reference_no, notes, created_by) values (?,?,?,?,?,?,?,?,?) returning id", Long.class,
                i, b.invoiceId(), amount, Date.valueOf(paidOn), mode, receipt, clip(b.reference(), 60), clip(b.notes(), 300), actor());
        audit.record("FEE_PAYMENT_RECEIVED", "PAYMENT", id, i, "Payment " + receipt + " of Rs " + amount.toPlainString() + " recorded");
        String payer = jdbc.query("select s.full_name from school_fee_records r join students s on s.id = r.student_id where r.id = ?", (rs, n) -> rs.getString(1), b.invoiceId()).stream().findFirst().orElse("a student");
        notify.add(i, "FEES", "Payment Received", "Fee payment of Rs " + amount.toPlainString() + " received for " + payer + " (" + receipt + ")", "/school/fees");
        return receipt(id);
    }

    @Transactional(readOnly = true)
    public Receipt receipt(Long paymentId) {
        long i = inst();
        return jdbc.query("select p.id, p.receipt_no, inst.name, p.paid_on, p.amount, p.method, p.reference_no, p.notes, v.invoice_no, v.fee_type, s.full_name, s.admission_no, c.name, sec.name, v.net, v.balance, p.created_by"
                + " from school_fee_payments p join school_invoice_v v on v.id = p.fee_record_id join students s on s.id = v.student_id join institutions inst on inst.id = p.institution_id left join school_classes c on c.id = s.school_class_id left join sections sec on sec.id = s.section_id where p.id = ? and p.institution_id = ?",
                (rs, n) -> new Receipt(rs.getLong(1), rs.getString(2), rs.getString(3), day(rs.getDate(4)), d(rs.getBigDecimal(5)), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), d(rs.getBigDecimal(15)), d(rs.getBigDecimal(16)), rs.getString(17)), paymentId, i)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Receipt"));
    }

    // ------------------------------------------------------------------ fee structure

    @Transactional(readOnly = true)
    public List<StructRow> structures(Long yearId, Long classId) {
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where f.institution_id = ?");
        if (yearId != null) { w.append(" and f.academic_year_id = ?"); a.add(yearId); }
        if (classId != null) { w.append(" and f.class_id = ?"); a.add(classId); }
        return jdbc.query("select f.id, f.academic_year_id, f.class_id, c.name, f.fee_head, f.frequency, f.amount, f.active, (select count(*) from school_fee_records r where r.structure_id = f.id) from school_fee_structures f join school_classes c on c.id = f.class_id" + w + " order by coalesce(c.grade_level, -1), c.name, f.fee_head",
                (rs, n) -> new StructRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), d(rs.getBigDecimal(7)), rs.getBoolean(8), rs.getLong(9)), a.toArray());
    }

    private record SC(String head, String freq, BigDecimal amount, boolean active) {}
    private SC checkStruct(StructBody b, Long selfId) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        String head = clip(b.feeHead(), 60);
        if (head == null) e.put("feeHead", "Required.");
        String freq = b.frequency() == null ? "" : b.frequency().trim().toUpperCase();
        if (!STRUCT_FREQ.contains(freq)) e.put("frequency", "Choose how often it is charged.");
        BigDecimal amount = money(b.amount(), "amount", e);
        if (b.classId() == null) e.put("classId", "Choose a grade.");
        else if (b.yearId() == null || count("select count(*) from school_classes where id = ? and institution_id = ? and academic_year_id = ?", b.classId(), i, b.yearId()) == 0) e.put("classId", "Choose a grade of that academic year.");
        if (head != null && b.classId() != null && count("select count(*) from school_fee_structures where class_id = ? and lower(fee_head) = lower(?) and id <> ?", b.classId(), head, selfId == null ? -1L : selfId) > 0) e.put("feeHead", "This grade already has that fee head.");
        fail(e);
        return new SC(head, freq, amount, b.active() == null || b.active());
    }

    public Map<String, Object> addStructure(StructBody b) {
        long i = inst();
        SC c = checkStruct(b, null);
        Long id = jdbc.queryForObject("insert into school_fee_structures (institution_id, academic_year_id, class_id, fee_head, frequency, amount, active) values (?,?,?,?,?,?,?) returning id", Long.class, i, b.yearId(), b.classId(), c.head(), c.freq(), c.amount(), c.active());
        audit.record("FEE_STRUCTURE_ADDED", "FEE_STRUCTURE", id, i, "Fee head " + c.head() + " added (Rs " + c.amount().toPlainString() + ")");
        return Map.of("id", id);
    }

    public Map<String, Object> updateStructure(Long id, StructBody b) {
        long i = inst();
        long billed = count("select count(*) from school_fee_records where structure_id = ?", id);
        if (count("select count(*) from school_fee_structures where id = ? and institution_id = ?", id, i) == 0) throw ApiException.notFound("Fee structure");
        SC c = checkStruct(b, id);
        jdbc.update("update school_fee_structures set academic_year_id = ?, class_id = ?, fee_head = ?, frequency = ?, amount = ?, active = ? where id = ?", b.yearId(), b.classId(), c.head(), c.freq(), c.amount(), c.active(), id);
        audit.record("FEE_STRUCTURE_UPDATED", "FEE_STRUCTURE", id, i, "Fee head " + c.head() + " updated" + (billed > 0 ? " (existing invoices keep their amounts)" : ""));
        return Map.of("id", id);
    }

    public void deleteStructure(Long id) {
        long i = inst();
        String head = jdbc.query("select fee_head from school_fee_structures where id = ? and institution_id = ?", (rs, n) -> rs.getString(1), id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Fee structure"));
        if (count("select count(*) from school_fee_records where structure_id = ?", id) > 0) throw ApiException.conflict("Invoices were generated from " + head + ". Set it to inactive instead of deleting it.");
        jdbc.update("delete from school_fee_structures where id = ?", id);
        audit.record("FEE_STRUCTURE_DELETED", "FEE_STRUCTURE", id, i, "Fee head " + head + " deleted");
    }

    /** Bills every active student of the grade (or all grades of the year) for each active fee head that has no invoice yet. */
    public GenResult generate(GenBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        if (b.yearId() == null || count("select count(*) from academic_years where id = ? and institution_id = ?", b.yearId(), i) == 0) e.put("yearId", "Choose an academic year.");
        LocalDate due = date(b.dueDate(), "dueDate", true, e);
        if (due != null && (due.isBefore(LocalDate.now().minusYears(1)) || due.isAfter(LocalDate.now().plusYears(2)))) e.put("dueDate", "Check the due date.");
        if (b.termId() != null && count("select count(*) from terms where id = ? and institution_id = ? and academic_year_id = ?", b.termId(), i, b.yearId()) == 0) e.put("termId", "Choose a term of that year.");
        if (b.classId() != null && count("select count(*) from school_classes where id = ? and institution_id = ? and academic_year_id = ?", b.classId(), i, b.yearId()) == 0) e.put("classId", "Choose a grade of that year.");
        fail(e);
        lockNumbers();
        String cls = b.classId() == null ? "" : " and st.class_id = " + b.classId();
        int skippedTerm = b.termId() == null ? (int) count("select count(*) from school_fee_structures st where st.institution_id = ? and st.academic_year_id = ? and st.active and st.frequency = 'TERM'" + cls, i, b.yearId()) : 0;
        List<Object[]> todo = jdbc.query("select s.id, st.id, st.fee_head, st.amount, st.frequency from students s join school_fee_structures st on st.class_id = s.school_class_id"
                + " where s.institution_id = ? and s.active and st.academic_year_id = ? and st.active" + cls + " and (st.frequency <> 'TERM' or ?::bigint is not null)"
                + " and not exists (select 1 from school_fee_records r where r.student_id = s.id and r.structure_id = st.id and coalesce(r.term_id, 0) = case when st.frequency = 'TERM' then coalesce(?::bigint, 0) else 0 end)"
                + " order by s.id, st.id limit 20000", (rs, n) -> new Object[] { rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBigDecimal(4), rs.getString(5) }, i, b.yearId(), b.termId(), b.termId());
        long next = nextNumber("school_fee_records", "invoice_no", "INV");
        java.util.Set<Object> students = new java.util.HashSet<>();
        for (Object[] r : todo) {
            jdbc.update("insert into school_fee_records (institution_id, student_id, academic_year_id, term_id, fee_type, amount, due_date, invoice_no, structure_id) values (?,?,?,?,?,?,?,?,?)",
                    i, r[0], b.yearId(), "TERM".equals(r[4]) ? b.termId() : null, r[2], r[3], Date.valueOf(due), no("INV", next++), r[1]);
            students.add(r[0]);
        }
        int pairs = (int) count("select count(*) from students s join school_fee_structures st on st.class_id = s.school_class_id where s.institution_id = ? and s.active and st.academic_year_id = ? and st.active" + cls + " and (st.frequency <> 'TERM' or ?::bigint is not null)", i, b.yearId(), b.termId());
        int existing = Math.max(0, pairs - todo.size());
        applyScholarships(b.yearId());
        audit.record("FEES_GENERATED", "FEE_STRUCTURE", b.yearId(), i, "Generated " + todo.size() + " invoices for " + students.size() + " students");
        return new GenResult(todo.size(), existing, skippedTerm, students.size());
    }

    // ------------------------------------------------------------------ concessions

    private static ConcRow conc(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConcRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7) == null ? null : rs.getDouble(7), d(rs.getBigDecimal(8)), rs.getString(9), rs.getString(10), iso(rs.getTimestamp(11)));
    }
    private static final String CONC_FROM = " from school_fee_concessions k join students s on s.id = k.student_id left join school_classes c on c.id = s.school_class_id left join school_fee_records r on r.id = k.fee_record_id";

    @Transactional(readOnly = true)
    public ConcPage concessions(Long classId, String status, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where k.institution_id = ?");
        if (classId != null) { w.append(" and s.school_class_id = ?"); a.add(classId); }
        if (status != null && !status.isBlank()) { w.append(" and k.status = ?"); a.add(status.trim().toUpperCase()); }
        long total = count("select count(*)" + CONC_FROM + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        return new ConcPage(jdbc.query("select k.id, s.full_name, s.admission_no, c.name, r.invoice_no, r.fee_type, k.percent, k.amount, k.reason, k.status, k.requested_at" + CONC_FROM + w + " order by (k.status = 'PENDING') desc, k.requested_at desc limit ? offset ?", (rs, n) -> conc(rs), a.toArray()), total, pg, sz);
    }

    public Map<String, Object> requestConcession(ConcBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        if (b.invoiceId() == null) e.put("invoiceId", "Choose the fee head.");
        if (b.percent() == null || b.percent().isNaN() || b.percent() <= 0 || b.percent() > 100) e.put("percent", "Enter a discount between 1 and 100%.");
        String reason = clip(b.reason(), 300);
        if (reason == null) e.put("reason", "Required.");
        fail(e);
        Object[] r = jdbc.query("select student_id, amount, fee_type, (select coalesce(sum(k.amount), 0) from school_fee_concessions k where k.fee_record_id = school_fee_records.id and k.status in ('PENDING','APPROVED')) from school_fee_records where id = ? and institution_id = ?",
                (rs, n) -> new Object[] { rs.getLong(1), rs.getBigDecimal(2), rs.getString(3), rs.getBigDecimal(4) }, b.invoiceId(), i).stream().findFirst().orElseThrow(() -> new FieldErrorsException(Map.of("invoiceId", "Choose an invoice of this school.")));
        BigDecimal amount = ((BigDecimal) r[1]).multiply(BigDecimal.valueOf(b.percent())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (((BigDecimal) r[3]).add(amount).compareTo((BigDecimal) r[1]) > 0) throw new FieldErrorsException(Map.of("percent", "Concessions on this fee would add up to more than the fee itself."));
        Long id = jdbc.queryForObject("insert into school_fee_concessions (institution_id, student_id, fee_record_id, amount, percent, reason) values (?,?,?,?,?,?) returning id", Long.class, i, r[0], b.invoiceId(), amount, BigDecimal.valueOf(b.percent()).setScale(2, RoundingMode.HALF_UP), reason);
        audit.record("CONCESSION_REQUESTED", "CONCESSION", id, i, b.percent() + "% concession requested on " + r[2]);
        notify.add(i, "FEES", "Concession awaiting approval", b.percent() + "% concession requested on " + r[2] + ".", "/school/fees");
        return Map.of("id", id);
    }

    public Map<String, Object> decideConcession(Long id, Boolean approve) {
        long i = inst();
        if (approve == null) throw ApiException.badRequest("Choose approve or reject.");
        Object[] k = jdbc.query("select k.status, k.amount, k.fee_record_id from school_fee_concessions k where k.id = ? and k.institution_id = ? for update", (rs, n) -> new Object[] { rs.getString(1), rs.getBigDecimal(2), rs.getObject(3) == null ? null : rs.getLong(3) }, id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Concession"));
        if (!"PENDING".equals(k[0])) throw ApiException.conflict("That request was already decided.");
        if (approve && k[2] != null) {
            BigDecimal bal = jdbc.queryForObject("select balance from school_invoice_v where id = ?", BigDecimal.class, k[2]);
            if (bal != null && bal.compareTo((BigDecimal) k[1]) < 0) throw ApiException.conflict("The student has already paid more than the discounted fee would be. Record a refund instead.");
        }
        String st = approve ? "APPROVED" : "REJECTED";
        jdbc.update("update school_fee_concessions set status = ?, decided_by = ?, decided_at = now() where id = ?", st, actor(), id);
        audit.record("CONCESSION_" + st, "CONCESSION", id, i, "Fee concession " + st.toLowerCase());
        return Map.of("id", id, "status", st);
    }

    public void withdrawConcession(Long id) {
        long i = inst();
        if (jdbc.update("delete from school_fee_concessions where id = ? and institution_id = ? and status = 'PENDING'", id, i) == 0) throw ApiException.conflict("Only pending requests can be withdrawn.");
        audit.record("CONCESSION_WITHDRAWN", "CONCESSION", id, i, "Fee concession request withdrawn");
    }

    // ------------------------------------------------------------------ scholarships

    private static final String SCH_STATUS = "case when k.status = 'REVOKED' then 'REVOKED' when k.validity = 'TERM' and t.end_date < current_date then 'EXPIRED' else 'ACTIVE' end";
    private static final String SCH_FROM = " from school_scholarships k join students s on s.id = k.student_id join academic_years y on y.id = k.academic_year_id left join terms t on t.id = k.term_id";

    @Transactional(readOnly = true)
    public ScholarPage scholarships(Long yearId, String status, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where k.institution_id = ?");
        if (yearId != null) { w.append(" and k.academic_year_id = ?"); a.add(yearId); }
        if (status != null && !status.isBlank()) { w.append(" and ").append(SCH_STATUS).append(" = ?"); a.add(status.trim().toUpperCase()); }
        long total = count("select count(*)" + SCH_FROM + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        return new ScholarPage(jdbc.query("select k.id, s.id, s.full_name, s.admission_no, k.name, k.award_amount, (select coalesce(sum(x.amount), 0) from school_scholarship_allocations x where x.scholarship_id = k.id), case when k.validity = 'TERM' then coalesce(t.name, 'Term') else 'Yearly' end, " + SCH_STATUS + ", y.name" + SCH_FROM + w + " order by k.awarded_at desc, k.id desc limit ? offset ?",
                (rs, n) -> new ScholarRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), d(rs.getBigDecimal(6)), d(rs.getBigDecimal(7)), rs.getString(8), rs.getString(9), rs.getString(10)), a.toArray()), total, pg, sz);
    }

    public Map<String, Object> award(ScholarBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        String name = clip(b.name(), 100);
        if (name == null) e.put("name", "Required.");
        BigDecimal amount = money(b.amount(), "amount", e);
        if (b.studentId() == null || count("select count(*) from students where id = ? and institution_id = ? and active", b.studentId(), i) == 0) e.put("studentId", "Choose an active student.");
        if (b.yearId() == null || count("select count(*) from academic_years where id = ? and institution_id = ?", b.yearId(), i) == 0) e.put("yearId", "Choose an academic year.");
        String validity = b.validity() == null ? "" : b.validity().trim().toUpperCase();
        if (!validity.equals("TERM") && !validity.equals("YEARLY")) e.put("validity", "Choose Term or Yearly.");
        if (validity.equals("TERM") && (b.termId() == null || count("select count(*) from terms where id = ? and institution_id = ? and academic_year_id = ?", b.termId(), i, b.yearId()) == 0)) e.put("termId", "Choose a term of that year.");
        fail(e);
        Long id = jdbc.queryForObject("insert into school_scholarships (institution_id, student_id, academic_year_id, term_id, name, award_amount, validity, awarded_by) values (?,?,?,?,?,?,?,?) returning id", Long.class,
                i, b.studentId(), b.yearId(), validity.equals("TERM") ? b.termId() : null, name, amount, validity, actor());
        BigDecimal applied = applyScholarship(id);
        audit.record("SCHOLARSHIP_AWARDED", "SCHOLARSHIP", id, i, name + " awarded (Rs " + amount.toPlainString() + ")");
        return Map.of("id", id, "applied", applied, "unapplied", amount.subtract(applied));
    }

    public Map<String, Object> revoke(Long id) {
        long i = inst();
        String name = jdbc.query("select name from school_scholarships where id = ? and institution_id = ? and status = 'ACTIVE'", (rs, n) -> rs.getString(1), id, i).stream().findFirst().orElseThrow(() -> ApiException.conflict("That scholarship is already revoked or doesn't exist."));
        jdbc.update("delete from school_scholarship_allocations where scholarship_id = ?", id);
        jdbc.update("update school_scholarships set status = 'REVOKED' where id = ?", id);
        audit.record("SCHOLARSHIP_REVOKED", "SCHOLARSHIP", id, i, name + " revoked; the discounted amounts are due again");
        return Map.of("id", id, "status", "REVOKED");
    }

    /** Takes the unapplied part of a scholarship off the student's unpaid invoices, earliest due date first. Returns the total applied so far. */
    private BigDecimal applyScholarship(long id) {
        Object[] k = jdbc.query("select student_id, academic_year_id, term_id, award_amount from school_scholarships where id = ? and status = 'ACTIVE'", (rs, n) -> new Object[] { rs.getLong(1), rs.getLong(2), rs.getObject(3) == null ? null : rs.getLong(3), rs.getBigDecimal(4) }, id).stream().findFirst().orElse(null);
        if (k == null) return BigDecimal.ZERO;
        BigDecimal applied = jdbc.queryForObject("select coalesce(sum(amount), 0) from school_scholarship_allocations where scholarship_id = ?", BigDecimal.class, id);
        BigDecimal left = ((BigDecimal) k[3]).subtract(applied);
        if (left.signum() <= 0) return applied;
        List<Object[]> recs = jdbc.query("select id, balance from school_invoice_v where student_id = ? and academic_year_id = ? and balance > 0" + (k[2] != null ? " and term_id = ?" : "") + " order by due_date nulls last, id",
                (rs, n) -> new Object[] { rs.getLong(1), rs.getBigDecimal(2) }, k[2] != null ? new Object[] { k[0], k[1], k[2] } : new Object[] { k[0], k[1] });
        for (Object[] r : recs) {
            if (left.signum() <= 0) break;
            BigDecimal take = left.min((BigDecimal) r[1]);
            jdbc.update("insert into school_scholarship_allocations (scholarship_id, fee_record_id, amount) values (?,?,?) on conflict (scholarship_id, fee_record_id) do update set amount = school_scholarship_allocations.amount + excluded.amount", id, r[0], take);
            left = left.subtract(take); applied = applied.add(take);
        }
        return applied;
    }

    private void applyScholarships(long yearId) {
        for (Long id : jdbc.queryForList("select k.id from school_scholarships k left join terms t on t.id = k.term_id where k.institution_id = ? and k.academic_year_id = ? and k.status = 'ACTIVE' and not (k.validity = 'TERM' and t.end_date < current_date)", Long.class, inst(), yearId)) applyScholarship(id);
    }

    // ------------------------------------------------------------------ refunds

    private static final String REF_FROM = " from school_fee_refunds f join school_fee_payments p on p.id = f.payment_id join school_fee_records r on r.id = p.fee_record_id join students s on s.id = r.student_id";

    @Transactional(readOnly = true)
    public RefundPage refunds(String from, String to, String status, int page, int size) {
        int sz = Math.max(1, Math.min(size, 100)), pg = Math.max(0, page);
        Map<String, String> e = new LinkedHashMap<>();
        LocalDate f = date(from, "from", false, e), t = date(to, "to", false, e);
        fail(e);
        List<Object> a = new ArrayList<>(List.of(inst()));
        StringBuilder w = new StringBuilder(" where f.institution_id = ?");
        if (f != null) { w.append(" and f.requested_at >= ?"); a.add(Timestamp.valueOf(f.atStartOfDay())); }
        if (t != null) { w.append(" and f.requested_at < ?"); a.add(Timestamp.valueOf(t.plusDays(1).atStartOfDay())); }
        if (status != null && !status.isBlank()) { w.append(" and f.status = ?"); a.add(status.trim().toUpperCase()); }
        long total = count("select count(*)" + REF_FROM + w, a.toArray());
        a.add(sz); a.add((long) pg * sz);
        return new RefundPage(jdbc.query("select f.id, 'REF-' || lpad(f.id::text, 4, '0'), s.full_name, s.admission_no, p.receipt_no, f.amount, f.reason, f.status, f.requested_at, f.decided_at" + REF_FROM + w + " order by f.requested_at desc, f.id desc limit ? offset ?",
                (rs, n) -> new RefundRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), d(rs.getBigDecimal(6)), rs.getString(7), rs.getString(8), iso(rs.getTimestamp(9)), iso(rs.getTimestamp(10))), a.toArray()), total, pg, sz);
    }

    public Map<String, Object> requestRefund(RefundBody b) {
        long i = inst();
        Map<String, String> e = new LinkedHashMap<>();
        BigDecimal amount = money(b.amount(), "amount", e);
        String reason = clip(b.reason(), 300);
        if (reason == null) e.put("reason", "Required.");
        if (b.paymentId() == null) e.put("paymentId", "Choose a receipt.");
        fail(e);
        Object[] p = jdbc.query("select p.receipt_no, p.amount - coalesce((select sum(f.amount) from school_fee_refunds f where f.payment_id = p.id and f.status in ('PENDING','REFUNDED')), 0) from school_fee_payments p where p.id = ? and p.institution_id = ?",
                (rs, n) -> new Object[] { rs.getString(1), rs.getBigDecimal(2) }, b.paymentId(), i).stream().findFirst().orElseThrow(() -> new FieldErrorsException(Map.of("paymentId", "Choose a receipt of this school.")));
        if (amount.compareTo((BigDecimal) p[1]) > 0) throw new FieldErrorsException(Map.of("amount", "Only " + ((BigDecimal) p[1]).toPlainString() + " of " + p[0] + " can still be refunded."));
        Long id = jdbc.queryForObject("insert into school_fee_refunds (institution_id, payment_id, amount, reason, requested_by) values (?,?,?,?,?) returning id", Long.class, i, b.paymentId(), amount, reason, actor());
        audit.record("REFUND_REQUESTED", "REFUND", id, i, "Refund of Rs " + amount.toPlainString() + " requested against " + p[0]);
        notify.add(i, "FEES", "Refund awaiting approval", "Refund of Rs " + amount.toPlainString() + " requested against " + p[0] + ".", "/school/fees");
        return Map.of("id", id);
    }

    public Map<String, Object> decideRefund(Long id, Boolean approve) {
        long i = inst();
        if (approve == null) throw ApiException.badRequest("Choose approve or reject.");
        String cur = jdbc.query("select status from school_fee_refunds where id = ? and institution_id = ? for update", (rs, n) -> rs.getString(1), id, i).stream().findFirst().orElseThrow(() -> ApiException.notFound("Refund"));
        if (!"PENDING".equals(cur)) throw ApiException.conflict("That refund was already decided.");
        String st = approve ? "REFUNDED" : "REJECTED";
        jdbc.update("update school_fee_refunds set status = ?, decided_by = ?, decided_at = now() where id = ?", st, actor(), id);
        audit.record("REFUND_" + st, "REFUND", id, i, "Refund " + (approve ? "marked as refunded" : "rejected"));
        return Map.of("id", id, "status", st);
    }
}
