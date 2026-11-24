package com.centram.classzy.school.admissions;

import java.util.Map;

import com.centram.classzy.school.admissions.AdmDto.*;
import com.centram.classzy.security.Roles;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/school/admissions")
@PreAuthorize(Roles.WRITE)
public class AdmissionsController {

    private final AdmissionsService s;

    public AdmissionsController(AdmissionsService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @GetMapping("/counts") public Counts counts() { return s.counts(); }

    @GetMapping("/applications")
    public AppPage applications(@RequestParam(name = "yearId", required = false) Long yearId, @RequestParam(name = "classId", required = false) Long classId, @RequestParam(name = "stage", required = false) String stage,
                                @RequestParam(name = "q", required = false) String q, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return s.applications(yearId, classId, stage, q, page, size);
    }
    @GetMapping("/applications/{id}") public AppDetail application(@PathVariable("id") Long id) { return s.application(id); }
    @PostMapping("/applications") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> create(@RequestBody AppBody b) { return s.createApplication(b); }
    @PutMapping("/applications/{id}") public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody AppBody b) { return s.updateApplication(id, b); }
    @PostMapping("/applications/{id}/submit") public Map<String, Object> submit(@PathVariable("id") Long id) { return s.submitApplication(id); }
    @PutMapping("/applications/{id}/stage") public Map<String, Object> stage(@PathVariable("id") Long id, @RequestBody StageBody b) { return s.setStage(id, b.stage()); }
    @DeleteMapping("/applications/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable("id") Long id) { s.deleteApplication(id); }

    @PostMapping("/applications/{id}/documents") @ResponseStatus(HttpStatus.CREATED)
    public DocRow addDoc(@PathVariable("id") Long id, @RequestParam(name = "type", required = false) String type, @RequestParam("file") MultipartFile file) { return s.addDocument(id, type, file); }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> doc(@PathVariable("id") Long id) {
        Object[] d = s.documentFile(id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename((String) d[0]).build().toString())
                .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.parseMediaType((String) d[1])).body((byte[]) d[2]);
    }
    @PutMapping("/documents/{id}/verify") public Map<String, Object> verify(@PathVariable("id") Long id, @RequestBody VerifyBody b) { return s.verifyDocument(id, b.verified()); }
    @DeleteMapping("/documents/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDoc(@PathVariable("id") Long id) { s.deleteDocument(id); }

    @GetMapping("/enquiries")
    public EnqPage enquiries(@RequestParam(name = "status", required = false) String status, @RequestParam(name = "q", required = false) String q, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return s.enquiries(status, q, page, size);
    }
    @PostMapping("/enquiries") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addEnquiry(@RequestBody EnqBody b) { return s.createEnquiry(b); }
    @PutMapping("/enquiries/{id}") public Map<String, Object> editEnquiry(@PathVariable("id") Long id, @RequestBody EnqBody b) { return s.updateEnquiry(id, b); }
    @PutMapping("/enquiries/{id}/status") public Map<String, Object> enquiryStatus(@PathVariable("id") Long id, @RequestBody StageBody b) { return s.setEnquiryStatus(id, b.stage()); }
    @DeleteMapping("/enquiries/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteEnquiry(@PathVariable("id") Long id) { s.deleteEnquiry(id); }

    @GetMapping("/waitlist") public Waitlist waitlist(@RequestParam(name = "classId", required = false) Long classId) { return s.waitlist(classId); }

    @GetMapping("/offers")
    public OfferPage offers(@RequestParam(name = "status", required = false) String status, @RequestParam(name = "q", required = false) String q, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return s.offers(status, q, page, size);
    }
    @PostMapping("/offers") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> createOffer(@RequestBody OfferBody b) { return s.createOffer(b); }
    @PostMapping("/offers/{id}/send") public Map<String, Object> send(@PathVariable("id") Long id) { return s.sendOffer(id); }
    @PostMapping("/offers/{id}/accept") public Map<String, Object> accept(@PathVariable("id") Long id) { return s.respond(id, true); }
    @PostMapping("/offers/{id}/decline") public Map<String, Object> decline(@PathVariable("id") Long id) { return s.respond(id, false); }
    @PostMapping("/offers/{id}/withdraw") public Map<String, Object> withdraw(@PathVariable("id") Long id) { return s.withdrawOffer(id); }
    @PostMapping("/offers/{id}/admit") public Map<String, Object> admit(@PathVariable("id") Long id, @RequestBody AdmitBody b) { return s.admit(id, b.sectionId()); }

    @GetMapping("/admitted")
    public AdmittedPage admitted(@RequestParam(name = "classId", required = false) Long classId, @RequestParam(name = "q", required = false) String q, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return s.admitted(classId, q, page, size);
    }
}
