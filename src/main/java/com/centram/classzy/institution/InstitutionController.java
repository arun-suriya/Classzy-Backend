package com.centram.classzy.institution;

import java.util.List;

import com.centram.classzy.institution.InstitutionDto.Detail;
import com.centram.classzy.institution.InstitutionDto.Input;
import com.centram.classzy.institution.InstitutionDto.Summary;
import com.centram.classzy.institution.InstitutionEnums.ImageKind;
import com.centram.classzy.security.Roles;

import jakarta.validation.Valid;

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
import org.springframework.web.multipart.MultipartFile;

/** Institution management. Super Admin only. */
@RestController
@RequestMapping("/api/institutions")
@PreAuthorize(Roles.SUPER)
public class InstitutionController {

    private final InstitutionService service;

    public InstitutionController(InstitutionService service) {
        this.service = service;
    }

    /** status: omit = all except archived; ARCHIVED, DRAFT, ACTIVE, TRIAL, EXPIRING_SOON, EXPIRED; or ALL. */
    @GetMapping
    public List<Summary> list(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String status) {
        return service.list(search, type, status);
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@Valid @RequestBody Input body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public Detail update(@PathVariable Long id, @Valid @RequestBody Input body) {
        return service.update(id, body);
    }

    /** Soft delete: archives the institution (its users can no longer sign in). Reversible with /restore. */
    @DeleteMapping("/{id}")
    public Detail archive(@PathVariable Long id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/restore")
    public Detail restore(@PathVariable Long id) {
        return service.restore(id);
    }

    /** Hard delete. Only works on archived institutions that have no dependent data. */
    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@PathVariable Long id) {
        service.deletePermanently(id);
    }

    @PutMapping("/{id}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        service.saveImage(id, ImageKind.LOGO, file);
    }

    @PutMapping("/{id}/banner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadBanner(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        service.saveImage(id, ImageKind.BANNER, file);
    }

    @DeleteMapping("/{id}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLogo(@PathVariable Long id) {
        service.deleteImage(id, ImageKind.LOGO);
    }

    @DeleteMapping("/{id}/banner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBanner(@PathVariable Long id) {
        service.deleteImage(id, ImageKind.BANNER);
    }
}
