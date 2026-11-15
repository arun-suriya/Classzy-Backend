package com.centram.classzy.communication;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.communication.CommDto.PushCategoryBody;
import com.centram.classzy.communication.CommDto.PushCategoryView;
import com.centram.classzy.communication.CommDto.SenderBody;
import com.centram.classzy.communication.CommDto.SenderView;
import com.centram.classzy.communication.CommDto.TemplateBody;
import com.centram.classzy.communication.CommDto.TemplateView;
import com.centram.classzy.communication.CommDto.Variable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SMS sender ids, message templates (email / SMS / push) and push notification categories. */
@Service
@Transactional
public class CommListsService {

    private static final Set<String> CHANNELS = Set.of("EMAIL", "SMS", "PUSH");
    private static final Set<String> SENDER_TYPES = Set.of("ALPHANUMERIC", "NUMERIC");
    private static final Set<String> SENDER_STATUS = Set.of("APPROVED", "PENDING", "REJECTED");

    static final List<Variable> VARIABLES = List.of(
            new Variable("{{student_name}}", "Name of the student"), new Variable("{{institution_name}}", "Name of the institution"),
            new Variable("{{class_name}}", "Class name"), new Variable("{{section_name}}", "Section name"),
            new Variable("{{parent_name}}", "Name of the parent/guardian"), new Variable("{{subject_name}}", "Subject name"),
            new Variable("{{exam_date}}", "Exam date"), new Variable("{{fee_amount}}", "Fee amount"), new Variable("{{due_date}}", "Due date"),
            new Variable("{{route_name}}", "Transport route name"), new Variable("{{book_title}}", "Library book title"),
            new Variable("{{event_name}}", "Event name"), new Variable("{{message}}", "Custom message"));

    private final CurrentUser current;
    private final CommSettingsStore store;
    private final SmsSenderIdRepository senders;
    private final CommTemplateRepository templates;
    private final PushCategoryRepository categories;
    private final AuditService audit;

    public CommListsService(CurrentUser current, CommSettingsStore store, SmsSenderIdRepository senders, CommTemplateRepository templates,
                            PushCategoryRepository categories, AuditService audit) {
        this.current = current;
        this.store = store;
        this.senders = senders;
        this.templates = templates;
        this.categories = categories;
        this.audit = audit;
    }

    /** Safe on every request: only the first call per institution adds the starter templates and push categories. */
    private void ensure(Long inst) {
        if (!store.markSeeded(inst)) return;
        Object[][] cats = {
                {"Admissions", "Application status, admission updates", true}, {"Attendance", "Attendance alerts and updates", true},
                {"Examinations", "Exam schedules, results, reminders", true}, {"Fees & Payments", "Fee reminders, payment confirmations", true},
                {"Transport", "Pickup/drop notifications, delays", true}, {"Library", "Book due reminders, reservations", true},
                {"Announcements", "General announcements and notices", true}, {"Events & Activities", "Event invitations and updates", true},
                {"Marketing / Newsletters", "Promotions and newsletters", false}};
        for (Object[] c : cats) {
            PushCategory p = new PushCategory();
            p.setInstitutionId(inst);
            p.name = (String) c[0];
            p.description = (String) c[1];
            p.active = (Boolean) c[2];
            categories.save(p);
        }
        String[][] seed = {
                {"EMAIL", "Welcome Email", "ADMISSIONS", "Welcome to {{institution_name}}", "Dear {{parent_name}},\n\nWelcome to {{institution_name}}. We are glad to have {{student_name}} with us."},
                {"EMAIL", "Admission Confirmation", "ADMISSIONS", "Admission confirmed: {{student_name}}", "Dear {{parent_name}},\n\nThe admission of {{student_name}} to {{institution_name}} has been confirmed."},
                {"EMAIL", "Fee Due Reminder", "FEES", "Fee due on {{due_date}}", "Dear {{parent_name}},\n\nThe fee of {{fee_amount}} for {{student_name}} is due on {{due_date}}."},
                {"EMAIL", "Attendance Alert", "ATTENDANCE", "{{student_name}} was absent today", "Dear {{parent_name}},\n\n{{student_name}} of {{class_name}} {{section_name}} was marked absent today."},
                {"EMAIL", "Exam Schedule", "EXAMINATIONS", "Upcoming exam schedule", "Dear {{parent_name}},\n\nThe {{subject_name}} exam for {{student_name}} is on {{exam_date}}."},
                {"SMS", "Admission Confirmation", "ADMISSIONS", null, "Dear {{parent_name}}, the admission of {{student_name}} to {{institution_name}} has been confirmed."},
                {"SMS", "Fee Due Reminder", "FEES", null, "Dear {{parent_name}}, the fee of {{fee_amount}} for {{student_name}} is due on {{due_date}}."},
                {"SMS", "Attendance Alert", "ATTENDANCE", null, "{{student_name}} is absent today ({{due_date}})."},
                {"SMS", "Transport Delay", "TRANSPORT", null, "Bus {{route_name}} is delayed. We will update you shortly."},
                {"SMS", "Exam Schedule", "EXAMINATIONS", null, "Your exam for {{subject_name}} is on {{exam_date}}."},
                {"PUSH", "Admission Confirmation", "ADMISSIONS", "Welcome to {{institution_name}}!", "Dear {{student_name}}, your admission has been confirmed."},
                {"PUSH", "Attendance Alert", "ATTENDANCE", "Absent today", "{{student_name}} is marked absent today."},
                {"PUSH", "Fee Due Reminder", "FEES", "Fee due", "Your fee of {{fee_amount}} is due on {{due_date}}."},
                {"PUSH", "Exam Schedule", "EXAMINATIONS", "Upcoming exam", "Your {{subject_name}} exam is on {{exam_date}}."},
                {"PUSH", "General Announcement", "ANNOUNCEMENTS", "Important notice", "{{message}}"}};
        for (String[] r : seed) {
            CommTemplate t = new CommTemplate();
            t.setInstitutionId(inst);
            t.channel = r[0];
            t.name = r[1];
            t.category = r[2];
            t.title = r[3];
            t.body = r[4];
            templates.save(t);
        }
    }

    public List<Variable> variables() { return VARIABLES; }

    // ------------------------------------------------------------------ SMS sender ids

    @Transactional(readOnly = true)
    public List<SenderView> listSenders() {
        return senders.findByInstitutionIdOrderByIdAsc(current.institutionId()).stream().map(SenderView::of).toList();
    }

    public SenderView createSender(SenderBody b) {
        Long inst = current.institutionId();
        SmsSenderId s = new SmsSenderId();
        s.setInstitutionId(inst);
        applySender(s, b, inst, 0L);
        s = senders.saveAndFlush(s);
        if (s.defaultSender) senders.clearDefaultExcept(inst, s.getId());
        audit.record("SMS_SENDER_CREATED", "SmsSenderId", s.getId(), inst, "SMS sender id " + s.senderId + " added");
        return SenderView.of(s);
    }

    public SenderView updateSender(Long id, SenderBody b) {
        Long inst = current.institutionId();
        SmsSenderId s = senders.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Sender ID"));
        applySender(s, b, inst, id);
        s = senders.saveAndFlush(s);
        if (s.defaultSender) senders.clearDefaultExcept(inst, s.getId());
        audit.record("SMS_SENDER_UPDATED", "SmsSenderId", id, inst, "SMS sender id " + s.senderId + " updated");
        return SenderView.of(s);
    }

    public void deleteSender(Long id) {
        Long inst = current.institutionId();
        SmsSenderId s = senders.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Sender ID"));
        senders.delete(s);
        audit.record("SMS_SENDER_DELETED", "SmsSenderId", id, inst, "SMS sender id " + s.senderId + " deleted");
    }

    private void applySender(SmsSenderId s, SenderBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String id = trim(b.senderId());
        String type = b.senderType() == null ? "" : b.senderType();
        if (id.isEmpty()) err.put("senderId", "Sender ID is required.");
        else if (type.equals("ALPHANUMERIC") && !id.matches("[A-Za-z0-9]{3,11}")) err.put("senderId", "Use 3 to 11 letters or digits, no spaces.");
        else if (type.equals("NUMERIC") && !id.matches("\\+?\\d{6,15}")) err.put("senderId", "Use 6 to 15 digits, optionally starting with +.");
        else if (senders.existsByInstitutionIdAndSenderIdIgnoreCaseAndIdNot(inst, id, self)) err.put("senderId", "This sender ID already exists.");
        if (!SENDER_TYPES.contains(type)) err.put("senderType", "Choose a type.");
        if (b.status() == null || !SENDER_STATUS.contains(b.status())) err.put("status", "Choose a status.");
        if (b.defaultSender() && b.status() != null && !b.status().equals("APPROVED")) err.put("defaultSender", "Only an approved sender ID can be the default.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        s.senderId = id;
        s.senderType = type;
        s.status = b.status();
        s.defaultSender = b.defaultSender();
    }

    // ------------------------------------------------------------------ templates

    @Transactional
    public List<TemplateView> listTemplates(String channel) {
        Long inst = current.institutionId();
        ensure(inst);
        List<CommTemplate> rows = channel == null || channel.isBlank()
                ? templates.findByInstitutionIdOrderByChannelAscNameAsc(inst)
                : templates.findByInstitutionIdAndChannelOrderByNameAsc(inst, channel.toUpperCase());
        return rows.stream().map(TemplateView::of).toList();
    }

    public TemplateView createTemplate(TemplateBody b) {
        Long inst = current.institutionId();
        ensure(inst);
        CommTemplate t = new CommTemplate();
        t.setInstitutionId(inst);
        applyTemplate(t, b, inst, 0L);
        t = templates.saveAndFlush(t);
        audit.record("COMM_TEMPLATE_CREATED", "CommTemplate", t.getId(), inst, t.channel + " template " + t.name + " created");
        return TemplateView.of(t);
    }

    public TemplateView updateTemplate(Long id, TemplateBody b) {
        Long inst = current.institutionId();
        CommTemplate t = templates.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Template"));
        applyTemplate(t, b, inst, id);
        t = templates.saveAndFlush(t);
        audit.record("COMM_TEMPLATE_UPDATED", "CommTemplate", id, inst, t.channel + " template " + t.name + " updated");
        return TemplateView.of(t);
    }

    public void deleteTemplate(Long id) {
        Long inst = current.institutionId();
        CommTemplate t = templates.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Template"));
        templates.delete(t);
        audit.record("COMM_TEMPLATE_DELETED", "CommTemplate", id, inst, t.channel + " template " + t.name + " deleted");
    }

    private void applyTemplate(CommTemplate t, TemplateBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String channel = b.channel() == null ? "" : b.channel().toUpperCase();
        String name = trim(b.name());
        String title = trim(b.title());
        String body = b.body() == null ? "" : b.body().trim();
        if (!CHANNELS.contains(channel)) err.put("channel", "Choose a channel.");
        if (name.isEmpty()) err.put("name", "Template name is required.");
        else if (name.length() > 100) err.put("name", "Name is too long.");
        else if (CHANNELS.contains(channel) && templates.existsByInstitutionIdAndChannelAndNameIgnoreCaseAndIdNot(inst, channel, name, self)) err.put("name", "A template with this name already exists.");
        if (b.category() == null || !CommSpec.CATEGORIES.contains(b.category())) err.put("category", "Choose a category.");
        if (!channel.equals("SMS") && title.isEmpty()) err.put("title", channel.equals("EMAIL") ? "Subject is required." : "Title is required.");
        if (title.length() > 150) err.put("title", "At most 150 characters.");
        int max = channel.equals("SMS") ? 640 : 2000;
        if (body.isEmpty()) err.put("body", "Message is required.");
        else if (body.length() > max) err.put("body", "At most " + max + " characters.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        t.channel = channel;
        t.name = name;
        t.category = b.category();
        t.title = channel.equals("SMS") ? null : title;
        t.body = body;
        t.active = b.active();
        t.updatedAt = LocalDateTime.now();
    }

    // ------------------------------------------------------------------ push categories

    @Transactional
    public List<PushCategoryView> listCategories() {
        Long inst = current.institutionId();
        ensure(inst);
        return categories.findByInstitutionIdOrderByIdAsc(inst).stream().map(PushCategoryView::of).toList();
    }

    public PushCategoryView createCategory(PushCategoryBody b) {
        Long inst = current.institutionId();
        ensure(inst);
        PushCategory c = new PushCategory();
        c.setInstitutionId(inst);
        applyCategory(c, b, inst, 0L);
        c = categories.saveAndFlush(c);
        audit.record("PUSH_CATEGORY_CREATED", "PushCategory", c.getId(), inst, "Push category " + c.name + " created");
        return PushCategoryView.of(c);
    }

    public PushCategoryView updateCategory(Long id, PushCategoryBody b) {
        Long inst = current.institutionId();
        PushCategory c = categories.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Category"));
        applyCategory(c, b, inst, id);
        c = categories.saveAndFlush(c);
        audit.record("PUSH_CATEGORY_UPDATED", "PushCategory", id, inst, "Push category " + c.name + " updated");
        return PushCategoryView.of(c);
    }

    public void deleteCategory(Long id) {
        Long inst = current.institutionId();
        PushCategory c = categories.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Category"));
        categories.delete(c);
        audit.record("PUSH_CATEGORY_DELETED", "PushCategory", id, inst, "Push category " + c.name + " deleted");
    }

    private void applyCategory(PushCategory c, PushCategoryBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = trim(b.name());
        if (name.isEmpty()) err.put("name", "Category name is required.");
        else if (name.length() > 60) err.put("name", "Name is too long.");
        else if (categories.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A category with this name already exists.");
        if (b.description() != null && b.description().length() > 200) err.put("description", "At most 200 characters.");
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        c.name = name;
        c.description = b.description() == null || b.description().isBlank() ? null : b.description().trim();
        c.active = b.active();
        c.mobile = b.mobile();
        c.web = b.web();
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
