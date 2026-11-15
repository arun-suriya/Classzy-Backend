package com.centram.classzy.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.security.AppPrincipal;
import com.centram.classzy.support.SupportDto.Article;
import com.centram.classzy.support.SupportDto.ArticleBody;
import com.centram.classzy.support.SupportDto.Attachment;
import com.centram.classzy.support.SupportDto.CreateBody;
import com.centram.classzy.support.SupportDto.Detail;
import com.centram.classzy.support.SupportDto.Event;
import com.centram.classzy.support.SupportDto.Message;
import com.centram.classzy.support.SupportDto.MessageBody;
import com.centram.classzy.support.SupportDto.Metric;
import com.centram.classzy.support.SupportDto.Option;
import com.centram.classzy.support.SupportDto.Options;
import com.centram.classzy.support.SupportDto.Page;
import com.centram.classzy.support.SupportDto.Summary;
import com.centram.classzy.support.SupportDto.Ticket;
import com.centram.classzy.support.SupportDto.UpdateBody;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class SupportService {

    static final List<String> CATEGORIES = List.of("Access Issue", "Data Management", "Finance", "Communication", "Academic", "Library", "User Management", "Transport", "Reports", "Mobile App", "Other");
    static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "AWAITING_CUSTOMER", "RESOLVED", "CLOSED");
    private static final long MAX_FILE = 5L * 1024 * 1024;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final String SEL = "select t.id, t.ticket_no, t.subject, t.category, t.priority, t.status, t.institution_id, i.name, t.requester, t.assigned_to, u.full_name, t.escalated, t.created_by, t.created_at, t.updated_at "
            + "from support_tickets t left join institutions i on i.id = t.institution_id left join users u on u.id = t.assigned_to";

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public SupportService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private static String iso(Timestamp t) { return t == null ? null : t.toInstant().toString(); }

    private static Ticket ticket(ResultSet rs) throws SQLException {
        return new Ticket(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7) == null ? null : rs.getLong(7), rs.getString(8), rs.getString(9),
                rs.getObject(10) == null ? null : rs.getLong(10), rs.getString(11), rs.getBoolean(12), rs.getString(13), iso(rs.getTimestamp(14)), iso(rs.getTimestamp(15)));
    }

    private AppPrincipal me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppPrincipal p ? p : null;
    }

    private String actor() {
        AppPrincipal p = me();
        if (p == null) return null;
        List<String> n = jdbc.queryForList("select full_name from users where id = ?", String.class, p.userId());
        return n.isEmpty() ? p.email() : n.get(0);
    }

    private void event(Long ticketId, String text) {
        jdbc.update("insert into ticket_events (ticket_id, actor, text) values (?, ?, ?)", ticketId, actor(), text.length() > 300 ? text.substring(0, 300) : text);
    }

    private static String nice(String code) {
        String s = code.replace('_', ' ').toLowerCase();
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    // ------------------------------------------------------------------ tickets

    @Transactional(readOnly = true)
    public Page list(String tab, String status, String priority, String category, Long institutionId, String q, String from, String to, int page, int size) {
        StringBuilder w = new StringBuilder(" where 1 = 1");
        List<Object> p = new ArrayList<>();
        String t = tab == null ? "ALL" : tab.toUpperCase();
        switch (t) {
            case "MINE" -> { AppPrincipal me = me(); w.append(" and t.assigned_to = ?"); p.add(me == null ? -1L : me.userId()); }
            case "UNASSIGNED" -> w.append(" and t.assigned_to is null and t.status <> 'CLOSED'");
            case "ESCALATED" -> w.append(" and t.escalated and t.status not in ('RESOLVED','CLOSED')");
            case "CLOSED" -> w.append(" and t.status = 'CLOSED'");
            default -> { }
        }
        if (status != null && !status.isBlank()) { w.append(" and t.status = ?"); p.add(status); }
        if (priority != null && !priority.isBlank()) { w.append(" and t.priority = ?"); p.add(priority); }
        if (category != null && !category.isBlank()) { w.append(" and t.category = ?"); p.add(category); }
        if (institutionId != null) { w.append(" and t.institution_id = ?"); p.add(institutionId); }
        if (q != null && !q.isBlank()) {
            w.append(" and (lower(t.ticket_no) like ? or lower(t.subject) like ? or lower(coalesce(i.name,'')) like ? or lower(coalesce(t.requester,'')) like ? or lower(coalesce(u.full_name,'')) like ?)");
            String k = "%" + q.trim().toLowerCase() + "%";
            for (int n = 0; n < 5; n++) p.add(k);
        }
        if (from != null && !from.isBlank()) { w.append(" and t.created_at >= ?"); p.add(Timestamp.from(date(from).atStartOfDay(IST).toInstant())); }
        if (to != null && !to.isBlank()) { w.append(" and t.created_at < ?"); p.add(Timestamp.from(date(to).plusDays(1).atStartOfDay(IST).toInstant())); }
        long total = jdbc.queryForObject("select count(*) from support_tickets t left join institutions i on i.id = t.institution_id left join users u on u.id = t.assigned_to" + w, Long.class, p.toArray());
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 1);
        List<Object> p2 = new ArrayList<>(p);
        p2.add(sz); p2.add((pg - 1) * sz);
        List<Ticket> rows = jdbc.query(SEL + w + " order by t.created_at desc, t.id desc limit ? offset ?", (rs, i) -> ticket(rs), p2.toArray());
        return new Page(rows, total, pg, sz);
    }

    private static LocalDate date(String s) {
        try { return LocalDate.parse(s); } catch (DateTimeParseException e) { throw ApiException.badRequest("Invalid date: " + s); }
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return new Summary(metric(null), metric("OPEN"), metric("IN_PROGRESS"), metric("AWAITING_CUSTOMER"), metric("RESOLVED"));
    }

    private Metric metric(String status) {
        String f = status == null ? "" : " and status = '" + status + "'";
        long now = jdbc.queryForObject("select count(*) from support_tickets where 1 = 1" + f, Long.class);
        long thisM = jdbc.queryForObject("select count(*) from support_tickets where created_at >= date_trunc('month', now())" + f, Long.class);
        long lastM = jdbc.queryForObject("select count(*) from support_tickets where created_at >= date_trunc('month', now()) - interval '1 month' and created_at < date_trunc('month', now())" + f, Long.class);
        Double change = lastM == 0 ? null : Math.round((thisM - lastM) * 1000.0 / lastM) / 10.0;
        return new Metric(now, change);
    }

    @Transactional(readOnly = true)
    public Options options() {
        return new Options(CATEGORIES.stream().map(c -> new Option(c, c)).toList(),
                jdbc.query("select id, full_name from users where role = 'SUPER_ADMIN' and active order by lower(full_name)", (rs, i) -> new Option(String.valueOf(rs.getLong(1)), rs.getString(2))),
                jdbc.query("select id, name from institutions where lifecycle <> 'ARCHIVED' order by lower(name)", (rs, i) -> new Option(String.valueOf(rs.getLong(1)), rs.getString(2))));
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        Ticket t = find(id);
        String desc = jdbc.queryForObject("select description from support_tickets where id = ?", String.class, id);
        int msgs = jdbc.queryForObject("select count(*) from ticket_messages where ticket_id = ?", Integer.class, id);
        int atts = jdbc.queryForObject("select count(*) from ticket_attachments where ticket_id = ?", Integer.class, id);
        return new Detail(t, desc, msgs, atts);
    }

    private Ticket find(Long id) {
        List<Ticket> r = jdbc.query(SEL + " where t.id = ?", (rs, i) -> ticket(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("Ticket");
        return r.get(0);
    }

    private void checkAssignee(Long id, Map<String, String> err) {
        if (id != null && jdbc.queryForObject("select count(*) from users where id = ? and role = 'SUPER_ADMIN' and active", Integer.class, id) == 0) err.put("assigneeId", "Choose an active support agent.");
    }

    public Detail create(CreateBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String subject = b.subject() == null ? "" : b.subject().trim();
        String desc = b.description() == null ? "" : b.description().trim();
        String cat = b.category() == null ? "" : b.category().trim();
        String pr = b.priority() == null || b.priority().isBlank() ? "MEDIUM" : b.priority().trim().toUpperCase();
        String req = b.requester() == null || b.requester().isBlank() ? null : b.requester().trim();
        if (subject.isEmpty()) err.put("subject", "Required."); else if (subject.length() > 200) err.put("subject", "Use 200 characters or fewer.");
        if (desc.isEmpty()) err.put("description", "Describe the issue."); else if (desc.length() > 5000) err.put("description", "Use 5000 characters or fewer.");
        if (!CATEGORIES.contains(cat)) err.put("category", "Choose a category.");
        if (!PRIORITIES.contains(pr)) err.put("priority", "Choose a priority.");
        if (req != null && req.length() > 150) err.put("requester", "Use 150 characters or fewer.");
        if (b.institutionId() != null && jdbc.queryForObject("select count(*) from institutions where id = ?", Integer.class, b.institutionId()) == 0) err.put("institutionId", "Institution not found.");
        checkAssignee(b.assigneeId(), err);
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        AppPrincipal me = me();
        Long id = jdbc.queryForObject("insert into support_tickets (ticket_no, subject, description, category, priority, institution_id, requester, assigned_to, created_by) "
                + "values ('TKT-' || to_char(now() at time zone 'Asia/Kolkata', 'YYYY') || '-' || lpad(nextval('support_ticket_seq')::text, 3, '0'), ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                Long.class, subject, desc, cat, pr, b.institutionId(), req, b.assigneeId(), me == null ? null : me.email());
        event(id, "Ticket created");
        if (b.assigneeId() != null) event(id, "Assigned to " + jdbc.queryForObject("select full_name from users where id = ?", String.class, b.assigneeId()));
        Ticket t = find(id);
        audit.record("TICKET_CREATED", "TICKET", id, b.institutionId(), "Created " + t.ticketNo() + ": " + subject);
        return detail(id);
    }

    public Detail update(Long id, UpdateBody b) {
        Ticket cur = find(id);
        Map<String, String> err = new LinkedHashMap<>();
        String status = b.status() == null ? cur.status() : b.status().trim().toUpperCase();
        String pr = b.priority() == null ? cur.priority() : b.priority().trim().toUpperCase();
        String cat = b.category() == null ? cur.category() : b.category().trim();
        if (!STATUSES.contains(status)) err.put("status", "Choose a status.");
        if (!PRIORITIES.contains(pr)) err.put("priority", "Choose a priority.");
        if (!CATEGORIES.contains(cat)) err.put("category", "Choose a category.");
        checkAssignee(b.assigneeId(), err);
        if (!err.isEmpty()) throw new FieldErrorsException(err);

        if (!status.equals(cur.status())) event(id, "Status changed from " + nice(cur.status()) + " to " + nice(status));
        if (!pr.equals(cur.priority())) event(id, "Priority changed from " + nice(cur.priority()) + " to " + nice(pr));
        if (!cat.equals(cur.category())) event(id, "Category changed to " + cat);
        if (!java.util.Objects.equals(b.assigneeId(), cur.assignedTo())) {
            event(id, b.assigneeId() == null ? "Unassigned" : "Assigned to " + jdbc.queryForObject("select full_name from users where id = ?", String.class, b.assigneeId()));
        }
        jdbc.update("update support_tickets set status = ?, priority = ?, category = ?, assigned_to = ?, updated_at = now(), "
                + "resolved_at = case when ? = 'RESOLVED' then coalesce(resolved_at, now()) when ? in ('OPEN','IN_PROGRESS','AWAITING_CUSTOMER') then null else resolved_at end, "
                + "closed_at = case when ? = 'CLOSED' then coalesce(closed_at, now()) else null end where id = ?", status, pr, cat, b.assigneeId(), status, status, status, id);
        audit.record("TICKET_UPDATED", "TICKET", id, null, "Updated " + cur.ticketNo() + (status.equals(cur.status()) ? "" : " → " + nice(status)));
        return detail(id);
    }

    public Detail escalate(Long id) {
        Ticket cur = find(id);
        if (cur.status().equals("CLOSED") || cur.status().equals("RESOLVED")) throw ApiException.conflict("A resolved or closed ticket can't be escalated.");
        if (cur.escalated()) throw ApiException.conflict("Already escalated.");
        String pr = cur.priority().equals("LOW") || cur.priority().equals("MEDIUM") ? "HIGH" : cur.priority();
        jdbc.update("update support_tickets set escalated = true, priority = ?, updated_at = now() where id = ?", pr, id);
        event(id, "Escalated" + (pr.equals(cur.priority()) ? "" : " (priority raised to " + nice(pr) + ")"));
        audit.record("TICKET_ESCALATED", "TICKET", id, null, "Escalated " + cur.ticketNo());
        return detail(id);
    }

    public void delete(Long id) {
        Ticket t = find(id);
        jdbc.update("delete from support_tickets where id = ?", id);
        audit.record("TICKET_DELETED", "TICKET", id, null, "Deleted " + t.ticketNo());
    }

    // ------------------------------------------------------------------ messages, history, attachments

    @Transactional(readOnly = true)
    public List<Message> messages(Long id) {
        find(id);
        return jdbc.query("select id, author, body, internal, created_at from ticket_messages where ticket_id = ? order by created_at, id",
                (rs, i) -> new Message(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), iso(rs.getTimestamp(5))), id);
    }

    public Message addMessage(Long id, MessageBody b) {
        find(id);
        String body = b.body() == null ? "" : b.body().trim();
        if (body.isEmpty()) throw new FieldErrorsException(Map.of("body", "Write a message."));
        if (body.length() > 5000) throw new FieldErrorsException(Map.of("body", "Use 5000 characters or fewer."));
        boolean internal = Boolean.TRUE.equals(b.internal());
        Long mid = jdbc.queryForObject("insert into ticket_messages (ticket_id, author, body, internal) values (?, ?, ?, ?) returning id", Long.class, id, actor(), body, internal);
        jdbc.update("update support_tickets set updated_at = now() where id = ?", id);
        event(id, internal ? "Internal note added" : "Reply added");
        return jdbc.query("select id, author, body, internal, created_at from ticket_messages where id = ?", (rs, i) -> new Message(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), iso(rs.getTimestamp(5))), mid).get(0);
    }

    @Transactional(readOnly = true)
    public List<Event> events(Long id) {
        find(id);
        return jdbc.query("select actor, text, created_at from ticket_events where ticket_id = ? order by created_at desc, id desc", (rs, i) -> new Event(rs.getString(1), rs.getString(2), iso(rs.getTimestamp(3))), id);
    }

    @Transactional(readOnly = true)
    public List<Attachment> attachments(Long id) {
        find(id);
        return jdbc.query("select id, file_name, content_type, size_bytes, uploaded_by, created_at from ticket_attachments where ticket_id = ? order by id",
                (rs, i) -> new Attachment(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), iso(rs.getTimestamp(6))), id);
    }

    public Attachment addAttachment(Long id, MultipartFile file) {
        find(id);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to attach.");
        if (file.getSize() > MAX_FILE) throw ApiException.badRequest("Files can be up to 5 MB.");
        if (jdbc.queryForObject("select count(*) from ticket_attachments where ticket_id = ?", Integer.class, id) >= 10) throw ApiException.conflict("A ticket can have up to 10 attachments.");
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[\\\\/]+", "_").trim();
        if (name.isEmpty()) name = "file";
        if (name.length() > 200) name = name.substring(name.length() - 200);
        byte[] data;
        try { data = file.getBytes(); } catch (java.io.IOException e) { throw ApiException.badRequest("Couldn't read that file."); }
        Long aid = jdbc.queryForObject("insert into ticket_attachments (ticket_id, file_name, content_type, size_bytes, data, uploaded_by) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class, id, name, file.getContentType(), data.length, data, actor());
        jdbc.update("update support_tickets set updated_at = now() where id = ?", id);
        event(id, "Attachment added: " + name);
        return jdbc.query("select id, file_name, content_type, size_bytes, uploaded_by, created_at from ticket_attachments where id = ?",
                (rs, i) -> new Attachment(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), iso(rs.getTimestamp(6))), aid).get(0);
    }

    /** Returns {fileName, contentType, bytes}. */
    @Transactional(readOnly = true)
    public Object[] attachmentData(Long attachmentId) {
        List<Object[]> r = jdbc.query("select file_name, content_type, data from ticket_attachments where id = ?", (rs, i) -> new Object[] { rs.getString(1), rs.getString(2), rs.getBytes(3) }, attachmentId);
        if (r.isEmpty()) throw ApiException.notFound("Attachment");
        return r.get(0);
    }

    public void deleteAttachment(Long attachmentId) {
        List<Object[]> r = jdbc.query("select ticket_id, file_name from ticket_attachments where id = ?", (rs, i) -> new Object[] { rs.getLong(1), rs.getString(2) }, attachmentId);
        if (r.isEmpty()) throw ApiException.notFound("Attachment");
        jdbc.update("delete from ticket_attachments where id = ?", attachmentId);
        event((Long) r.get(0)[0], "Attachment removed: " + r.get(0)[1]);
    }

    // ------------------------------------------------------------------ knowledge base

    @Transactional(readOnly = true)
    public List<Article> articles(String q) {
        String k = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        return jdbc.query("select id, title, category, body, published, created_by, updated_at from kb_articles" + (k == null ? "" : " where lower(title) like ? or lower(body) like ?") + " order by updated_at desc, id desc",
                (rs, i) -> new Article(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getString(6), iso(rs.getTimestamp(7))), k == null ? new Object[0] : new Object[] { k, k });
    }

    private Map<String, String> articleErrors(ArticleBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        if (b.title() == null || b.title().isBlank()) err.put("title", "Required."); else if (b.title().trim().length() > 200) err.put("title", "Use 200 characters or fewer.");
        if (b.category() == null || !CATEGORIES.contains(b.category().trim())) err.put("category", "Choose a category.");
        if (b.body() == null || b.body().isBlank()) err.put("body", "Required."); else if (b.body().length() > 10000) err.put("body", "Use 10000 characters or fewer.");
        return err;
    }

    public Article createArticle(ArticleBody b) {
        Map<String, String> err = articleErrors(b);
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        Long id = jdbc.queryForObject("insert into kb_articles (title, category, body, published, created_by) values (?, ?, ?, ?, ?) returning id", Long.class, b.title().trim(), b.category().trim(), b.body().trim(), !Boolean.FALSE.equals(b.published()), actor());
        audit.record("KB_ARTICLE_CREATED", "KB_ARTICLE", id, null, "Created article " + b.title().trim());
        return article(id);
    }

    public Article updateArticle(Long id, ArticleBody b) {
        article(id);
        Map<String, String> err = articleErrors(b);
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        jdbc.update("update kb_articles set title = ?, category = ?, body = ?, published = ?, updated_at = now() where id = ?", b.title().trim(), b.category().trim(), b.body().trim(), !Boolean.FALSE.equals(b.published()), id);
        audit.record("KB_ARTICLE_UPDATED", "KB_ARTICLE", id, null, "Updated article " + b.title().trim());
        return article(id);
    }

    public void deleteArticle(Long id) {
        Article a = article(id);
        jdbc.update("delete from kb_articles where id = ?", id);
        audit.record("KB_ARTICLE_DELETED", "KB_ARTICLE", id, null, "Deleted article " + a.title());
    }

    private Article article(Long id) {
        List<Article> r = jdbc.query("select id, title, category, body, published, created_by, updated_at from kb_articles where id = ?",
                (rs, i) -> new Article(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getString(6), iso(rs.getTimestamp(7))), id);
        if (r.isEmpty()) throw ApiException.notFound("Article");
        return r.get(0);
    }
}
