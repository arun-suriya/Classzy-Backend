package com.centram.classzy.school.settings;

import java.util.List;
import java.util.Map;

import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.institution.InstitutionEnums.ImageKind;
import com.centram.classzy.institution.InstitutionService;
import com.centram.classzy.school.settings.SetDto.*;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/school/settings")
@PreAuthorize(Roles.WRITE)
public class SetController {
    private final SetService s;
    private final InstitutionService institutions;
    private final CurrentUser current;

    public SetController(SetService s, InstitutionService institutions, CurrentUser current) { this.s = s; this.institutions = institutions; this.current = current; }

    @GetMapping public All all() { return s.all(); }
    @PutMapping("/general") @ResponseStatus(HttpStatus.NO_CONTENT) public void general(@RequestBody GeneralBody b) { s.saveGeneral(b); }
    @PutMapping("/academic") @ResponseStatus(HttpStatus.NO_CONTENT) public void academic(@RequestBody AcademicBody b) { s.saveAcademic(b); }
    @PutMapping("/admissions") @ResponseStatus(HttpStatus.NO_CONTENT) public void admissions(@RequestBody AdmissionsBody b) { s.saveAdmissions(b); }
    @PutMapping("/fees") @ResponseStatus(HttpStatus.NO_CONTENT) public void fees(@RequestBody FeesBody b) { s.saveFees(b); }
    @PutMapping("/integrations/{service}") @ResponseStatus(HttpStatus.NO_CONTENT) public void integration(@PathVariable("service") String service, @RequestBody IntegrationBody b) { s.saveIntegration(service, b.provider()); }

    @PutMapping("/logo") @ResponseStatus(HttpStatus.NO_CONTENT) public void logo(@RequestParam("file") MultipartFile file) { institutions.saveImage(current.institutionId(), ImageKind.LOGO, file); }
    @DeleteMapping("/logo") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteLogo() { institutions.deleteImage(current.institutionId(), ImageKind.LOGO); }

    @GetMapping("/users") public List<SchoolUser> users(@RequestParam(name = "q", required = false) String q, @RequestParam(name = "role", required = false) String role) { return s.users(q, role); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) public Invited invite(@RequestBody InviteBody b) { return s.invite(b); }
    @PutMapping("/users/{id}/role") public SchoolUser role(@PathVariable("id") Long id, @RequestBody RoleBody b) { return s.changeRole(id, b.role()); }
    @PutMapping("/users/{id}/status") public SchoolUser status(@PathVariable("id") Long id, @RequestBody StatusBody b) { return s.setStatus(id, b.status()); }
    @PostMapping("/users/{id}/reset-password") public Map<String, String> reset(@PathVariable("id") Long id) { return Map.of("temporaryPassword", s.resetPassword(id)); }
}
