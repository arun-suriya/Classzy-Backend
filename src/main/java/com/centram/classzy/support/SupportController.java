package com.centram.classzy.support;

import java.util.List;

import com.centram.classzy.support.SupportDto.Article;
import com.centram.classzy.support.SupportDto.ArticleBody;
import com.centram.classzy.support.SupportDto.Attachment;
import com.centram.classzy.support.SupportDto.CreateBody;
import com.centram.classzy.support.SupportDto.Detail;
import com.centram.classzy.support.SupportDto.Event;
import com.centram.classzy.support.SupportDto.Message;
import com.centram.classzy.support.SupportDto.MessageBody;
import com.centram.classzy.support.SupportDto.Options;
import com.centram.classzy.support.SupportDto.Page;
import com.centram.classzy.support.SupportDto.Summary;
import com.centram.classzy.support.SupportDto.UpdateBody;
import com.centram.classzy.security.Roles;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

/** Support & Tickets (Super Admin). */
@RestController
@RequestMapping("/api/support")
@PreAuthorize(Roles.SUPER)
public class SupportController {

    private final SupportService service;

    public SupportController(SupportService service) { this.service = service; }

    @GetMapping("/tickets")
    public Page list(@RequestParam(name = "tab", defaultValue = "ALL") String tab, @RequestParam(name = "status", required = false) String status,
                     @RequestParam(name = "priority", required = false) String priority, @RequestParam(name = "category", required = false) String category,
                     @RequestParam(name = "institutionId", required = false) Long institutionId, @RequestParam(name = "q", required = false) String q,
                     @RequestParam(name = "from", required = false) String from, @RequestParam(name = "to", required = false) String to,
                     @RequestParam(name = "page", defaultValue = "1") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return service.list(tab, status, priority, category, institutionId, q, from, to, page, size);
    }

    @GetMapping("/summary")
    public Summary summary() { return service.summary(); }

    @GetMapping("/options")
    public Options options() { return service.options(); }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@RequestBody CreateBody b) { return service.create(b); }

    @GetMapping("/tickets/{id}")
    public Detail detail(@PathVariable("id") Long id) { return service.detail(id); }

    @PutMapping("/tickets/{id}")
    public Detail update(@PathVariable("id") Long id, @RequestBody UpdateBody b) { return service.update(id, b); }

    @PostMapping("/tickets/{id}/escalate")
    public Detail escalate(@PathVariable("id") Long id) { return service.escalate(id); }

    @DeleteMapping("/tickets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) { service.delete(id); }

    @GetMapping("/tickets/{id}/messages")
    public List<Message> messages(@PathVariable("id") Long id) { return service.messages(id); }

    @PostMapping("/tickets/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Message addMessage(@PathVariable("id") Long id, @RequestBody MessageBody b) { return service.addMessage(id, b); }

    @GetMapping("/tickets/{id}/events")
    public List<Event> events(@PathVariable("id") Long id) { return service.events(id); }

    @GetMapping("/tickets/{id}/attachments")
    public List<Attachment> attachments(@PathVariable("id") Long id) { return service.attachments(id); }

    @PostMapping("/tickets/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public Attachment upload(@PathVariable("id") Long id, @RequestParam("file") MultipartFile file) { return service.addAttachment(id, file); }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable("attachmentId") Long attachmentId) {
        Object[] a = service.attachmentData(attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename((String) a[0], java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body((byte[]) a[2]);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable("attachmentId") Long attachmentId) { service.deleteAttachment(attachmentId); }

    @GetMapping("/articles")
    public List<Article> articles(@RequestParam(name = "q", required = false) String q) { return service.articles(q); }

    @PostMapping("/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public Article createArticle(@RequestBody ArticleBody b) { return service.createArticle(b); }

    @PutMapping("/articles/{id}")
    public Article updateArticle(@PathVariable("id") Long id, @RequestBody ArticleBody b) { return service.updateArticle(id, b); }

    @DeleteMapping("/articles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArticle(@PathVariable("id") Long id) { service.deleteArticle(id); }
}
