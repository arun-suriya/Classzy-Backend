package com.centram.classzy.institution;

import java.time.Duration;

import com.centram.classzy.institution.InstitutionEnums.ImageKind;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logos and banners are served without a token so a plain <img src> works. Only branding is exposed here.
 * The UI adds ?v=<updatedAt> so a changed image is fetched fresh.
 */
@RestController
@RequestMapping("/api/public/institutions")
public class PublicInstitutionController {

    private final InstitutionService service;

    public PublicInstitutionController(InstitutionService service) {
        this.service = service;
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        return image(id, ImageKind.LOGO);
    }

    @GetMapping("/{id}/banner")
    public ResponseEntity<byte[]> banner(@PathVariable Long id) {
        return image(id, ImageKind.BANNER);
    }

    private ResponseEntity<byte[]> image(Long id, ImageKind kind) {
        InstitutionImage img = service.image(id, kind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(img.getData());
    }
}
