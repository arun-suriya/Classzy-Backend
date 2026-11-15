package com.centram.classzy.comms;

import com.centram.classzy.comms.CommsDto.Body;
import com.centram.classzy.comms.CommsDto.Options;
import com.centram.classzy.comms.CommsDto.Page;
import com.centram.classzy.comms.CommsDto.ResolveBody;
import com.centram.classzy.comms.CommsDto.Resolved;
import com.centram.classzy.comms.CommsDto.Row;
import com.centram.classzy.comms.CommsDto.Summary;
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

/** Platform communications (Super Admin). Separate from the per-institution notification settings under /api/settings. */
@RestController
@RequestMapping("/api/communications")
@PreAuthorize(Roles.SUPER)
public class CommsController {

    private final CommsService service;

    public CommsController(CommsService service) { this.service = service; }

    @GetMapping
    public Page list(@RequestParam(name = "type", required = false) String type, @RequestParam(name = "status", required = false) String status,
                     @RequestParam(name = "audience", required = false) String audience, @RequestParam(name = "q", required = false) String q,
                     @RequestParam(name = "from", required = false) String from, @RequestParam(name = "to", required = false) String to,
                     @RequestParam(name = "page", defaultValue = "1") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return service.list(type, status, audience, q, from, to, page, size);
    }

    @GetMapping("/summary")
    public Summary summary() { return service.summary(); }

    @GetMapping("/options")
    public Options options() { return service.options(); }

    @PostMapping("/resolve")
    public Resolved resolve(@RequestBody ResolveBody b) { return service.resolve(b.audienceKind(), b.targets()); }

    @GetMapping("/{id}")
    public Row get(@PathVariable("id") Long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Row create(@RequestBody Body b) { return service.create(b); }

    @PutMapping("/{id}")
    public Row update(@PathVariable("id") Long id, @RequestBody Body b) { return service.update(id, b); }

    @PostMapping("/{id}/send")
    public Row send(@PathVariable("id") Long id) { return service.send(id); }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public Row duplicate(@PathVariable("id") Long id) { return service.duplicate(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) { service.delete(id); }
}
