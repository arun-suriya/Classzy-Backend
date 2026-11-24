package com.centram.classzy.school.comm;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.comm.MsgDto.*;
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
@RequestMapping("/api/school/communication")
@PreAuthorize(Roles.WRITE)
public class MsgController {

    private final MsgService s;
    public MsgController(MsgService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }

    @GetMapping("/preview")
    public Preview preview(@RequestParam(name = "audience") String a, @RequestParam(name = "classId", required = false) Long c, @RequestParam(name = "sectionId", required = false) Long sec, @RequestParam(name = "channel") String ch) { return s.preview(a, c, sec, ch); }

    @PostMapping("/messages") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> send(@RequestBody SendBody b) { return s.send(b); }
    @GetMapping("/messages")
    public MsgPage messages(@RequestParam(name = "from", required = false) String f, @RequestParam(name = "to", required = false) String t, @RequestParam(name = "channel", required = false) String ch, @RequestParam(name = "status", required = false) String st,
                            @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return s.messages(f, t, ch, st, p, z); }
    @GetMapping("/messages/{id}") public MsgDetail message(@PathVariable("id") Long id) { return s.message(id); }
    @PostMapping("/messages/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancel(@PathVariable("id") Long id) { s.cancel(id); }

    @GetMapping("/templates") public List<TplRow> templates(@RequestParam(name = "q", required = false) String q, @RequestParam(name = "category", required = false) String c) { return s.templates(q, c); }
    @PostMapping("/templates") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addTemplate(@RequestBody TplBody b) { return s.addTemplate(b); }
    @PostMapping("/templates/starter") public Map<String, Object> starter() { return s.starterTemplates(); }
    @PutMapping("/templates/{id}") public Map<String, Object> updateTemplate(@PathVariable("id") Long id, @RequestBody TplBody b) { return s.updateTemplate(id, b); }
    @DeleteMapping("/templates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteTemplate(@PathVariable("id") Long id) { s.deleteTemplate(id); }

    @GetMapping("/notifications")
    public NotifPage notifications(@RequestParam(name = "kind", required = false) String k, @RequestParam(name = "unread", defaultValue = "false") boolean u, @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return s.notifications(k, u, p, z); }
    @GetMapping("/notifications/unread-count") public Map<String, Long> unread() { return Map.of("unread", s.unread()); }
    @PostMapping("/notifications/read-all") @ResponseStatus(HttpStatus.NO_CONTENT) public void readAll() { s.markAllRead(); }
    @PostMapping("/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable("id") Long id) { s.markRead(id); }

    @GetMapping("/announcements")
    public AnnPage announcements(@RequestParam(name = "audience", required = false) String a, @RequestParam(name = "status", required = false) String st, @RequestParam(name = "page", defaultValue = "0") int p, @RequestParam(name = "size", defaultValue = "10") int z) { return s.announcements(a, st, p, z); }
    @PostMapping("/announcements") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addAnnouncement(@RequestBody AnnBody b) { return s.addAnnouncement(b); }
    @PutMapping("/announcements/{id}") public Map<String, Object> updateAnnouncement(@PathVariable("id") Long id, @RequestBody AnnBody b) { return s.updateAnnouncement(id, b); }
    @PutMapping("/announcements/{id}/status") public Map<String, Object> announcementStatus(@PathVariable("id") Long id, @RequestBody StatusBody b) { return s.announcementStatus(id, b.status()); }
    @DeleteMapping("/announcements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteAnnouncement(@PathVariable("id") Long id) { s.deleteAnnouncement(id); }
}
