package com.centram.classzy.school.students;

import java.util.Map;

import com.centram.classzy.school.students.StuDto.*;
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
@RequestMapping("/api/school/students")
@PreAuthorize(Roles.WRITE)
public class StudentsController {

    private final StudentsService s;
    public StudentsController(StudentsService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }

    @GetMapping
    public Page list(@RequestParam(name = "yearId", required = false) Long yearId, @RequestParam(name = "classId", required = false) Long classId, @RequestParam(name = "sectionId", required = false) Long sectionId,
                     @RequestParam(name = "status", required = false) String status, @RequestParam(name = "q", required = false) String q,
                     @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) {
        return s.list(yearId, classId, sectionId, status, q, page, size);
    }
    @GetMapping("/{id}") public Detail get(@PathVariable("id") Long id) { return s.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> create(@RequestBody Body b) { return s.create(b); }
    @PutMapping("/{id}") public Map<String, Object> update(@PathVariable("id") Long id, @RequestBody Body b) { return s.update(id, b); }
    @PutMapping("/{id}/status") public Map<String, Object> status(@PathVariable("id") Long id, @RequestBody StatusBody b) { return s.setStatus(id, b.active(), b.reason()); }

    @PostMapping("/{id}/documents") @ResponseStatus(HttpStatus.CREATED)
    public Doc addDoc(@PathVariable("id") Long id, @RequestParam(name = "type", required = false) String type, @RequestParam("file") MultipartFile file) { return s.addDocument(id, type, file); }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> doc(@PathVariable("id") Long id) {
        Object[] d = s.documentFile(id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename((String) d[0]).build().toString())
                .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.parseMediaType((String) d[1])).body((byte[]) d[2]);
    }
    @DeleteMapping("/documents/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDoc(@PathVariable("id") Long id) { s.deleteDocument(id); }
}
