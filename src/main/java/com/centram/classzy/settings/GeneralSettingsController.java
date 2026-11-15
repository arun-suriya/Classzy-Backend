package com.centram.classzy.settings;

import com.centram.classzy.security.Roles;
import com.centram.classzy.settings.GeneralSettingsService.General;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/general")
@PreAuthorize(Roles.READ)
public class GeneralSettingsController {

    private final GeneralSettingsService service;

    public GeneralSettingsController(GeneralSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public General get() { return service.get(); }

    @PutMapping
    @PreAuthorize(Roles.WRITE)
    public General save(@RequestBody General g) { return service.update(g); }
}
