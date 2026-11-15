package com.centram.classzy.sysmgmt;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.centram.classzy.security.Roles;
import com.centram.classzy.sysmgmt.SysDto.BackupBody;
import com.centram.classzy.sysmgmt.SysDto.BackupRow;
import com.centram.classzy.sysmgmt.SysDto.InstanceBody;
import com.centram.classzy.sysmgmt.SysDto.InstanceRow;
import com.centram.classzy.sysmgmt.SysDto.IntegrationBody;
import com.centram.classzy.sysmgmt.SysDto.IntegrationRow;
import com.centram.classzy.sysmgmt.SysDto.Overview;
import com.centram.classzy.sysmgmt.SysDto.ServiceRow;
import com.centram.classzy.sysmgmt.SysDto.SettingsBody;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** System Management (Super Admin). The existing /api/system/health endpoints are unchanged. */
@RestController
@RequestMapping("/api/sysmgmt")
@PreAuthorize(Roles.SUPER)
public class SysController {

    private final SysService service;

    public SysController(SysService service) { this.service = service; }

    @GetMapping("/overview")
    public Overview overview() { return service.overview(); }

    @PostMapping("/monitoring/run")
    public List<ServiceRow> run() { return service.runChecks(); }

    @PostMapping("/instances")
    @ResponseStatus(HttpStatus.CREATED)
    public InstanceRow createInstance(@RequestBody InstanceBody b) { return service.createInstance(b); }

    @PutMapping("/instances/{id}")
    public InstanceRow updateInstance(@PathVariable("id") Long id, @RequestBody InstanceBody b) { return service.updateInstance(id, b); }

    @PostMapping("/instances/{id}/refresh")
    public InstanceRow refresh(@PathVariable("id") Long id) { return service.refreshInstance(id); }

    @DeleteMapping("/instances/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInstance(@PathVariable("id") Long id) { service.deleteInstance(id); }

    @PostMapping("/integrations")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationRow createIntegration(@RequestBody IntegrationBody b) { return service.createIntegration(b); }

    @PutMapping("/integrations/{id}")
    public IntegrationRow updateIntegration(@PathVariable("id") Long id, @RequestBody IntegrationBody b) { return service.updateIntegration(id, b); }

    @PostMapping("/integrations/{id}/toggle")
    public IntegrationRow toggleIntegration(@PathVariable("id") Long id) { return service.toggleIntegration(id); }

    @PostMapping("/integrations/{id}/test")
    public IntegrationRow testIntegration(@PathVariable("id") Long id) { return service.testIntegration(id); }

    @DeleteMapping("/integrations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIntegration(@PathVariable("id") Long id) { service.deleteIntegration(id); }

    @GetMapping("/data/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable("type") String type) {
        byte[] csv = service.exportData(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("classzy-" + type.toLowerCase() + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @PutMapping("/settings/security")
    public Map<String, String> saveSecurity(@RequestBody SettingsBody b) { return service.saveSettings(true, b.values()); }

    @PutMapping("/settings/config")
    public Map<String, String> saveConfig(@RequestBody SettingsBody b) { return service.saveSettings(false, b.values()); }

    @PostMapping("/backups")
    @ResponseStatus(HttpStatus.CREATED)
    public BackupRow backup(@RequestBody BackupBody b) { return service.runBackup(b); }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("id") Long id) {
        Object[] d = service.backupData(id);
        String stamp = ZonedDateTime.ofInstant(((java.sql.Timestamp) d[2]).toInstant(), ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("classzy-" + ((String) d[0]).toLowerCase() + "-backup-" + stamp + ".zip").build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body((byte[]) d[3]);
    }

    @DeleteMapping("/backups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBackup(@PathVariable("id") Long id) { service.deleteBackup(id); }
}
