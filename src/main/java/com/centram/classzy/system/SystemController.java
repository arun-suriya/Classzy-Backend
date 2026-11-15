package com.centram.classzy.system;

import com.centram.classzy.security.Roles;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/health")
@PreAuthorize(Roles.SUPER)
public class SystemController {

    private final HealthService service;

    public SystemController(HealthService service) {
        this.service = service;
    }

    /** Runs a check and saves it to the log history. */
    @PostMapping("/run")
    public HealthService.Snapshot run() {
        return service.checkAndLog();
    }

    @GetMapping("/logs")
    public HealthService.LogPage logs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.page(page, size);
    }
}
