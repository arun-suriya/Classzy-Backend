package com.centram.classzy.communication;

import java.util.List;
import java.util.Map;

import com.centram.classzy.communication.CommDto.PushCategoryBody;
import com.centram.classzy.communication.CommDto.PushCategoryView;
import com.centram.classzy.communication.CommDto.SenderBody;
import com.centram.classzy.communication.CommDto.SenderView;
import com.centram.classzy.communication.CommDto.TemplateBody;
import com.centram.classzy.communication.CommDto.TemplateView;
import com.centram.classzy.communication.CommDto.TestResult;
import com.centram.classzy.communication.CommDto.Variable;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/communication")
@PreAuthorize(Roles.READ)
public class CommunicationController {

    private final CommunicationService settings;
    private final CommListsService lists;

    public CommunicationController(CommunicationService settings, CommListsService lists) {
        this.settings = settings;
        this.lists = lists;
    }

    // ---- settings sections (EMAIL, SMTP, SENDER, SIGNATURE, EMAIL_DELIVERY, SMS, SMS_PROVIDER, SMS_DELIVERY, SMS_REPORTS, PUSH, PUSH_MOBILE, PUSH_WEB, PUSH_DELIVERY)
    @GetMapping("/settings/{section}")
    public Map<String, String> get(@PathVariable("section") String section) { return settings.get(section); }

    @PutMapping("/settings/{section}")
    @PreAuthorize(Roles.WRITE)
    public Map<String, String> save(@PathVariable("section") String section, @RequestBody Map<String, String> body) { return settings.save(section, body); }

    @PostMapping("/test-connection/{kind}")
    @PreAuthorize(Roles.WRITE)
    public TestResult test(@PathVariable("kind") String kind) { return settings.testConnection(kind); }

    // ---- SMS sender ids
    @GetMapping("/sms-sender-ids")
    public List<SenderView> senders() { return lists.listSenders(); }

    @PostMapping("/sms-sender-ids")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public SenderView createSender(@RequestBody SenderBody b) { return lists.createSender(b); }

    @PutMapping("/sms-sender-ids/{id}")
    @PreAuthorize(Roles.WRITE)
    public SenderView updateSender(@PathVariable("id") Long id, @RequestBody SenderBody b) { return lists.updateSender(id, b); }

    @DeleteMapping("/sms-sender-ids/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSender(@PathVariable("id") Long id) { lists.deleteSender(id); }

    // ---- templates
    @GetMapping("/templates")
    public List<TemplateView> templates(@RequestParam(name = "channel", required = false) String channel) { return lists.listTemplates(channel); }

    @PostMapping("/templates")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView createTemplate(@RequestBody TemplateBody b) { return lists.createTemplate(b); }

    @PutMapping("/templates/{id}")
    @PreAuthorize(Roles.WRITE)
    public TemplateView updateTemplate(@PathVariable("id") Long id, @RequestBody TemplateBody b) { return lists.updateTemplate(id, b); }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(@PathVariable("id") Long id) { lists.deleteTemplate(id); }

    @GetMapping("/template-variables")
    public List<Variable> variables() { return lists.variables(); }

    // ---- push notification categories
    @GetMapping("/push-categories")
    public List<PushCategoryView> categories() { return lists.listCategories(); }

    @PostMapping("/push-categories")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public PushCategoryView createCategory(@RequestBody PushCategoryBody b) { return lists.createCategory(b); }

    @PutMapping("/push-categories/{id}")
    @PreAuthorize(Roles.WRITE)
    public PushCategoryView updateCategory(@PathVariable("id") Long id, @RequestBody PushCategoryBody b) { return lists.updateCategory(id, b); }

    @DeleteMapping("/push-categories/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable("id") Long id) { lists.deleteCategory(id); }
}
