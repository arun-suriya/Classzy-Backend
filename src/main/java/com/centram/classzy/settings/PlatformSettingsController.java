package com.centram.classzy.settings;

import java.util.Map;

import com.centram.classzy.security.Roles;
import com.centram.classzy.settings.PlatformSettingsService.Asset;
import com.centram.classzy.settings.PlatformSettingsService.Settings;

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

/** Platform Settings (Super Admin). Institution-level settings stay under /api/settings/general. */
@RestController
@RequestMapping("/api/platform-settings")
@PreAuthorize(Roles.SUPER)
public class PlatformSettingsController {

    private final PlatformSettingsService service;

    public PlatformSettingsController(PlatformSettingsService service) { this.service = service; }

    @GetMapping
    public Settings get() { return service.get(); }

    @PutMapping
    public Settings save(@RequestBody Map<String, String> body) { return service.save(body); }

    @PostMapping("/assets/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    public Asset upload(@PathVariable("kind") String kind, @RequestParam("file") MultipartFile file) { return service.upload(kind.toUpperCase(), file); }

    @GetMapping("/assets/{kind}")
    public ResponseEntity<byte[]> asset(@PathVariable("kind") String kind) {
        Object[] a = service.assetData(kind.toUpperCase());
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType((String) a[0])).body((byte[]) a[1]);
    }

    @DeleteMapping("/assets/{kind}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("kind") String kind) { service.removeAsset(kind.toUpperCase()); }
}
