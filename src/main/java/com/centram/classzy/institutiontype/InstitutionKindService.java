package com.centram.classzy.institutiontype;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.institution.InstitutionRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstitutionKindService {

    public record Input(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description, Boolean active) {
    }

    public record Row(Long id, String code, String name, String description, boolean active, boolean systemType,
                      long institutionCount) {
    }

    private final InstitutionKindRepository repo;
    private final InstitutionRepository institutions;
    private final AuditService audit;

    public InstitutionKindService(InstitutionKindRepository repo, InstitutionRepository institutions, AuditService audit) {
        this.repo = repo;
        this.institutions = institutions;
        this.audit = audit;
    }

    /** "Coaching Centre" -> COACHING_CENTRE. Used for codes, and to read the code a client sends. */
    public static String codeOf(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    @Transactional(readOnly = true)
    public List<Row> list() {
        Map<String, Long> counts = counts();
        return repo.findAllByOrderByNameAsc().stream().map(k -> toRow(k, counts)).toList();
    }

    @Transactional
    public Row create(Input in) {
        String name = in.name().trim();
        String code = codeOf(name);
        if (code.isEmpty()) throw ApiException.badRequest("Please enter a name that contains letters or numbers.");
        if (repo.existsByNameIgnoreCase(name) || repo.existsByCode(code)) {
            throw ApiException.conflict("An institution type named \"" + name + "\" already exists.");
        }
        var k = new InstitutionKind();
        k.setCode(code);
        k.setName(name);
        k.setDescription(blank(in.description()));
        k.setActive(in.active() == null || in.active());
        k = repo.save(k);
        audit.record("INSTITUTION_TYPE_CREATED", "INSTITUTION_TYPE", k.getId(), null, "Institution type \"" + name + "\" was created");
        return toRow(k, counts());
    }

    @Transactional
    public Row update(Long id, Input in) {
        InstitutionKind k = find(id);
        String name = in.name().trim();
        if (repo.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("An institution type named \"" + name + "\" already exists.");
        }
        k.setName(name);
        k.setDescription(blank(in.description()));
        if (in.active() != null) {
            if (!in.active() && "OTHER".equals(k.getCode())) {
                throw ApiException.conflict("The \"Other\" type can't be deactivated; it is the fallback for everything else.");
            }
            k.setActive(in.active());
        }
        k = repo.save(k);
        audit.record("INSTITUTION_TYPE_UPDATED", "INSTITUTION_TYPE", id, null, "Institution type \"" + name + "\" was updated");
        return toRow(k, counts());
    }

    @Transactional
    public void delete(Long id) {
        InstitutionKind k = find(id);
        if (k.isSystemType()) {
            throw ApiException.conflict("Built-in types can't be deleted. You can deactivate them instead.");
        }
        long used = institutions.countByInstitutionType(k.getCode());
        if (used > 0) {
            throw ApiException.conflict(used + (used == 1 ? " institution uses" : " institutions use")
                    + " this type. Deactivate it instead, or move them to another type first.");
        }
        repo.delete(k);
        audit.record("INSTITUTION_TYPE_DELETED", "INSTITUTION_TYPE", id, null, "Institution type \"" + k.getName() + "\" was deleted");
    }

    private InstitutionKind find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Institution type"));
    }

    private Map<String, Long> counts() {
        Map<String, Long> m = new HashMap<>();
        for (Object[] r : institutions.countGroupedByType()) m.put((String) r[0], ((Number) r[1]).longValue());
        return m;
    }

    private static Row toRow(InstitutionKind k, Map<String, Long> counts) {
        return new Row(k.getId(), k.getCode(), k.getName(), k.getDescription(), k.isActive(), k.isSystemType(),
                counts.getOrDefault(k.getCode(), 0L));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
