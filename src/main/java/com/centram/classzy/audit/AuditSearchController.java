package com.centram.classzy.audit;

import java.time.LocalDate;

import com.centram.classzy.audit.AuditSearchService.Detail;
import com.centram.classzy.audit.AuditSearchService.Options;
import com.centram.classzy.audit.AuditSearchService.Page;
import com.centram.classzy.audit.AuditSearchService.Summary;
import com.centram.classzy.security.Roles;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit Logs page (Super Admin). The older GET /api/audit-logs list stays as it was. */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize(Roles.SUPER)
public class AuditSearchController {

    private final AuditSearchService service;

    public AuditSearchController(AuditSearchService service) { this.service = service; }

    @GetMapping("/summary")
    public Summary summary() { return service.summary(); }

    @GetMapping("/options")
    public Options options() { return service.options(); }

    @GetMapping("/search")
    public Page search(@RequestParam(name = "q", required = false) String q, @RequestParam(name = "range", defaultValue = "30D") String range,
                       @RequestParam(name = "from", required = false) String from, @RequestParam(name = "to", required = false) String to,
                       @RequestParam(name = "event", required = false) String event, @RequestParam(name = "module", required = false) String module,
                       @RequestParam(name = "user", required = false) String user, @RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "sort", defaultValue = "desc") String sort, @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "10") int size) {
        return service.list(q, range, from, to, event, module, user, status, sort, page, size);
    }

    @GetMapping("/entries/{id}")
    public Detail detail(@PathVariable("id") long id) { return service.detail(id); }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(name = "q", required = false) String q, @RequestParam(name = "range", defaultValue = "30D") String range,
                                         @RequestParam(name = "from", required = false) String from, @RequestParam(name = "to", required = false) String to,
                                         @RequestParam(name = "event", required = false) String event, @RequestParam(name = "module", required = false) String module,
                                         @RequestParam(name = "user", required = false) String user, @RequestParam(name = "status", required = false) String status,
                                         @RequestParam(name = "sort", defaultValue = "desc") String sort) {
        byte[] csv = service.export(q, range, from, to, event, module, user, status, sort);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-logs-" + LocalDate.now() + ".csv").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)).body(csv);
    }
}
