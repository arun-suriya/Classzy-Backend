package com.centram.classzy.branch;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.institution.Institution;
import com.centram.classzy.institution.InstitutionEnums.Lifecycle;
import com.centram.classzy.institution.InstitutionRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

    public record Input(Long institutionId, @NotBlank @Size(max = 150) String name, @Size(max = 30) String code,
                        @Size(max = 10) String branchType, boolean main, @Size(max = 10) String status,
                        @Size(max = 150) String headName, @Size(max = 20) String phone, @Size(max = 200) String email,
                        @Size(max = 500) String address, @Size(max = 80) String city, @Size(max = 80) String state,
                        @Size(max = 12) String pinCode, Integer establishmentYear, @Size(max = 500) String notes) {
    }

    public record Row(Long id, Long institutionId, String institutionName, String name, String code, String branchType,
                      boolean main, String status, String headName, String phone, String email, String address,
                      String city, String state, String pinCode, Integer establishmentYear, String notes, Instant updatedAt) {
    }

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{6,15}$");

    private final BranchRepository repo;
    private final InstitutionRepository institutions;
    private final AuditService audit;

    public BranchService(BranchRepository repo, InstitutionRepository institutions, AuditService audit) {
        this.repo = repo;
        this.institutions = institutions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Row> list(Long institutionId, String search, String status, String type) {
        Map<Long, String> names = names();
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        var all = institutionId == null ? repo.findAll() : repo.findByInstitutionId(institutionId);
        return all.stream()
                .filter(b -> status == null || status.isBlank() || b.getStatus().equalsIgnoreCase(status))
                .filter(b -> type == null || type.isBlank() || b.getBranchType().equalsIgnoreCase(type))
                .filter(b -> q.isEmpty() || has(b.getName(), q) || has(b.getCode(), q) || has(b.getCity(), q)
                        || has(b.getHeadName(), q) || has(names.get(b.getInstitutionId()), q))
                .sorted(Comparator.comparing((Branch b) -> names.getOrDefault(b.getInstitutionId(), "").toLowerCase(Locale.ROOT))
                        .thenComparing((Branch b) -> !b.isMainBranch())
                        .thenComparing(b -> b.getName().toLowerCase(Locale.ROOT)))
                .map(b -> toRow(b, names)).toList();
    }

    @Transactional(readOnly = true)
    public Row get(Long id) {
        return toRow(find(id), names());
    }

    @Transactional
    public Row create(Input in) {
        if (in.institutionId() == null) throw new FieldErrorsException(Map.of("institutionId", "is required"));
        Institution inst = institutions.findById(in.institutionId()).orElseThrow(() -> ApiException.notFound("Institution"));
        if (inst.getLifecycle() == Lifecycle.ARCHIVED) {
            throw ApiException.conflict("This institution is archived. Restore it before adding branches.");
        }
        String name = in.name().trim();
        String code = blank(in.code());
        validate(in);
        if (repo.existsByInstitutionIdAndNameIgnoreCase(inst.getId(), name)) {
            throw new FieldErrorsException(Map.of("name", "a branch with this name already exists in " + inst.getName()));
        }
        if (code != null && repo.existsByInstitutionIdAndCodeIgnoreCase(inst.getId(), code)) {
            throw new FieldErrorsException(Map.of("code", "is already used by another branch of " + inst.getName()));
        }
        var b = new Branch();
        b.setInstitutionId(inst.getId());
        // the first branch of an institution is its main branch
        boolean main = in.main() || repo.countByInstitutionId(inst.getId()) == 0;
        if (main) repo.clearMain(inst.getId(), -1L);     // must happen before the insert: one main per institution
        apply(b, in, main);
        b = repo.save(b);
        audit.record("BRANCH_CREATED", "BRANCH", b.getId(), inst.getId(), "Branch \"" + name + "\" was added to " + inst.getName());
        return toRow(b, Map.of(inst.getId(), inst.getName()));
    }

    @Transactional
    public Row update(Long id, Input in) {
        Branch b = find(id);
        Institution inst = institutions.findById(b.getInstitutionId()).orElseThrow(() -> ApiException.notFound("Institution"));
        if (inst.getLifecycle() == Lifecycle.ARCHIVED) {
            throw ApiException.conflict("This institution is archived. Restore it before editing its branches.");
        }
        String name = in.name().trim();
        String code = blank(in.code());
        validate(in);
        if (repo.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(b.getInstitutionId(), name, id)) {
            throw new FieldErrorsException(Map.of("name", "a branch with this name already exists in " + inst.getName()));
        }
        if (code != null && repo.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(b.getInstitutionId(), code, id)) {
            throw new FieldErrorsException(Map.of("code", "is already used by another branch of " + inst.getName()));
        }
        if (b.isMainBranch() && !in.main()) {
            throw new FieldErrorsException(Map.of("main", "an institution always has a main branch. Mark another branch as main instead"));
        }
        if (in.main()) {
            repo.clearMain(b.getInstitutionId(), id);    // before the update, then reload because the query clears the session
            b = find(id);
        }
        apply(b, in, in.main());
        b = repo.save(b);
        audit.record("BRANCH_UPDATED", "BRANCH", id, b.getInstitutionId(), "Branch \"" + name + "\" of " + inst.getName() + " was updated");
        return toRow(b, Map.of(inst.getId(), inst.getName()));
    }

    @Transactional
    public void delete(Long id) {
        Branch b = find(id);
        if (b.isMainBranch() && repo.countByInstitutionId(b.getInstitutionId()) > 1) {
            throw ApiException.conflict("This is the main branch. Mark another branch as main before deleting it.");
        }
        repo.delete(b);
        audit.record("BRANCH_DELETED", "BRANCH", id, b.getInstitutionId(), "Branch \"" + b.getName() + "\" was deleted");
    }

    private void validate(Input in) {
        Map<String, String> e = new LinkedHashMap<>();
        String type = in.branchType() == null ? "BRANCH" : in.branchType().trim().toUpperCase(Locale.ROOT);
        String status = in.status() == null ? "ACTIVE" : in.status().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("BRANCH") && !type.equals("CAMPUS")) e.put("branchType", "must be CAMPUS or BRANCH");
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) e.put("status", "must be ACTIVE or INACTIVE");
        if (in.main() && status.equals("INACTIVE")) e.put("main", "the main branch must be active");
        String email = blank(in.email());
        if (email != null && !EMAIL.matcher(email).matches()) e.put("email", "must be a valid email address");
        String phone = blank(in.phone());
        if (phone != null && !PHONE.matcher(phone).matches()) e.put("phone", "must be 6-15 digits");
        if (in.establishmentYear() != null && (in.establishmentYear() < 1800 || in.establishmentYear() > LocalDate.now().getYear())) {
            e.put("establishmentYear", "is not a valid year");
        }
        if (!e.isEmpty()) throw new FieldErrorsException(e);
    }

    private void apply(Branch b, Input in, boolean main) {
        b.setName(in.name().trim());
        b.setCode(blank(in.code()) == null ? null : blank(in.code()).toUpperCase(Locale.ROOT));
        b.setBranchType(in.branchType() == null ? "BRANCH" : in.branchType().trim().toUpperCase(Locale.ROOT));
        b.setMainBranch(main);
        b.setStatus(in.status() == null ? "ACTIVE" : in.status().trim().toUpperCase(Locale.ROOT));
        b.setHeadName(blank(in.headName()));
        b.setPhone(blank(in.phone()));
        b.setEmail(blank(in.email()) == null ? null : blank(in.email()).toLowerCase(Locale.ROOT));
        b.setAddress(blank(in.address()));
        b.setCity(blank(in.city()));
        b.setState(blank(in.state()));
        b.setPinCode(blank(in.pinCode()));
        b.setEstablishmentYear(in.establishmentYear());
        b.setNotes(blank(in.notes()));
    }

    private Branch find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Branch"));
    }

    private Map<Long, String> names() {
        Map<Long, String> m = new HashMap<>();
        institutions.findAll().forEach(i -> m.put(i.getId(), i.getName()));
        return m;
    }

    private static Row toRow(Branch b, Map<Long, String> names) {
        return new Row(b.getId(), b.getInstitutionId(), names.getOrDefault(b.getInstitutionId(), "—"), b.getName(), b.getCode(),
                b.getBranchType(), b.isMainBranch(), b.getStatus(), b.getHeadName(), b.getPhone(), b.getEmail(), b.getAddress(),
                b.getCity(), b.getState(), b.getPinCode(), b.getEstablishmentYear(), b.getNotes(), b.getUpdatedAt());
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
