package com.centram.classzy.modules;

import java.util.List;

import com.centram.classzy.modules.ModulesDto.AccessBody;
import com.centram.classzy.modules.ModulesDto.AccessRow;
import com.centram.classzy.modules.ModulesDto.AuditItem;
import com.centram.classzy.modules.ModulesDto.BulkBody;
import com.centram.classzy.modules.ModulesDto.Detail;
import com.centram.classzy.modules.ModulesDto.MetaBody;
import com.centram.classzy.modules.ModulesDto.ModuleRow;
import com.centram.classzy.modules.ModulesDto.Overview;
import com.centram.classzy.modules.ModulesDto.SaveBody;
import com.centram.classzy.modules.ModulesDto.StatusBody;
import com.centram.classzy.modules.ModulesDto.Usage;
import com.centram.classzy.security.Roles;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Modules & Features (platform catalog). Super Admin only. */
@RestController
@RequestMapping("/api/modules")
@PreAuthorize(Roles.SUPER)
public class ModulesController {

    private final ModulesService service;

    public ModulesController(ModulesService service) { this.service = service; }

    @GetMapping
    public Overview overview() { return service.overview(); }

    @PutMapping("/status")
    public Overview bulk(@RequestBody BulkBody body) { return service.bulk(body.states()); }

    @GetMapping("/{code}")
    public Detail detail(@PathVariable("code") String code) { return service.detail(code); }

    @PutMapping("/{code}")
    public Detail meta(@PathVariable("code") String code, @RequestBody MetaBody body) { return service.meta(code, body); }

    @PutMapping("/{code}/status")
    public ModuleRow status(@PathVariable("code") String code, @RequestBody StatusBody body) { return service.setEnabled(code, Boolean.TRUE.equals(body.enabled())); }

    @PutMapping("/{code}/configuration")
    public Detail save(@PathVariable("code") String code, @RequestBody SaveBody body) { return service.save(code, body); }

    @PostMapping("/{code}/reset")
    public Detail reset(@PathVariable("code") String code) { return service.reset(code); }

    @GetMapping("/{code}/access")
    public List<AccessRow> access(@PathVariable("code") String code) { return service.access(code); }

    @PutMapping("/{code}/access/{institutionId}")
    public AccessRow setAccess(@PathVariable("code") String code, @PathVariable("institutionId") Long institutionId, @RequestBody AccessBody body) {
        return service.setAccess(code, institutionId, Boolean.TRUE.equals(body.enabled()));
    }

    @GetMapping("/{code}/usage")
    public Usage usage(@PathVariable("code") String code) { return service.usage(code); }

    @GetMapping("/{code}/audit")
    public List<AuditItem> audit(@PathVariable("code") String code) { return service.auditLog(code); }
}
