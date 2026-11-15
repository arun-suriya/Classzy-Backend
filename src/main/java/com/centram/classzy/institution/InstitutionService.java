package com.centram.classzy.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.institution.InstitutionDto.Detail;
import com.centram.classzy.institution.InstitutionDto.Input;
import com.centram.classzy.institution.InstitutionDto.Summary;
import com.centram.classzy.institution.InstitutionEnums.ImageKind;
import com.centram.classzy.institution.InstitutionEnums.Lifecycle;
import com.centram.classzy.institution.InstitutionEnums.PlanType;
import com.centram.classzy.institution.InstitutionEnums.Status;
import com.centram.classzy.institutiontype.InstitutionKind;
import com.centram.classzy.institutiontype.InstitutionKindRepository;
import com.centram.classzy.institutiontype.InstitutionKindService;
import com.centram.classzy.people.StudentRepository;
import com.centram.classzy.people.TeacherRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InstitutionService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9]{6,15}$");

    private final InstitutionRepository repo;
    private final InstitutionImageRepository images;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final AuditService audit;
    private final InstitutionKindRepository kinds;

    public InstitutionService(InstitutionRepository repo, InstitutionImageRepository images,
                              StudentRepository students, TeacherRepository teachers, AuditService audit,
                              InstitutionKindRepository kinds) {
        this.audit = audit;
        this.kinds = kinds;
        this.repo = repo;
        this.images = images;
        this.students = students;
        this.teachers = teachers;
    }

    // ---------------------------------------------------------------- reads

    /** status: null = everything except archived, ALL = everything, otherwise one status. */
    @Transactional(readOnly = true)
    public List<Summary> list(String search, String type, String status) {
        Map<Long, Long> studentCounts = toMap(students.countGroupedByInstitution());
        Map<Long, Long> teacherCounts = toMap(teachers.countGroupedByInstitution());
        var withLogo = new HashSet<>(images.institutionIdsWith(ImageKind.LOGO));
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        return repo.findAllByOrderByNameAsc().stream()
                .filter(i -> type == null || type.isBlank() || i.getInstitutionType().equalsIgnoreCase(type.trim()))
                .filter(i -> q.isEmpty() || contains(i.getName(), q) || contains(i.getCity(), q)
                        || contains(i.getEmail(), q) || contains(i.getContactName(), q))
                .filter(i -> matchesStatus(statusOf(i), status))
                .map(i -> new Summary(i.getId(), i.getName(), i.getInstitutionType(), statusOf(i), i.getContactName(),
                        i.getEmail(), i.getCity(), i.getState(), i.getPlanType(), i.getSubscriptionEnd(),
                        studentCounts.getOrDefault(i.getId(), 0L), teacherCounts.getOrDefault(i.getId(), 0L),
                        withLogo.contains(i.getId()), i.getCreatedAt(), i.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return toDetail(find(id));
    }

    // --------------------------------------------------------------- writes

    @Transactional
    public Detail create(Input in) {
        String name = in.name().trim();
        if (repo.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("An institution named \"" + name + "\" already exists.");
        }
        var i = new Institution();
        i.setLifecycle(in.draft() ? Lifecycle.DRAFT : Lifecycle.ACTIVE);
        validate(in, in.draft(), null);
        apply(i, in);
        i = repo.save(i);
        audit.record(in.draft() ? "INSTITUTION_DRAFT" : "INSTITUTION_CREATED", "INSTITUTION", i.getId(), i.getId(),
                "Institution \"" + i.getName() + "\" was " + (in.draft() ? "saved as a draft" : "created"));
        return toDetail(i);
    }

    @Transactional
    public Detail update(Long id, Input in) {
        Institution i = find(id);
        if (i.getLifecycle() == Lifecycle.ARCHIVED) {
            throw ApiException.conflict("This institution is archived. Restore it before editing.");
        }
        String name = in.name().trim();
        if (repo.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("An institution named \"" + name + "\" already exists.");
        }
        // A draft may stay a draft; saving without draft=true publishes it and needs the full form.
        boolean stillDraft = in.draft() && i.getLifecycle() == Lifecycle.DRAFT;
        validate(in, stillDraft, i.getInstitutionType());
        apply(i, in);
        i.setLifecycle(stillDraft ? Lifecycle.DRAFT : Lifecycle.ACTIVE);
        i = repo.save(i);
        audit.record("INSTITUTION_UPDATED", "INSTITUTION", i.getId(), i.getId(), "Institution \"" + i.getName() + "\" was updated");
        return toDetail(i);
    }

    /** DELETE in the API = archive. Nothing is lost; the institution can be restored. */
    @Transactional
    public Detail archive(Long id) {
        Institution i = find(id);
        if (i.getLifecycle() != Lifecycle.ARCHIVED) {
            i.setLifecycle(Lifecycle.ARCHIVED);
            i.setArchivedAt(Instant.now());
            audit.record("INSTITUTION_ARCHIVED", "INSTITUTION", i.getId(), i.getId(), "Institution \"" + i.getName() + "\" was archived");
        }
        return toDetail(repo.save(i));
    }

    @Transactional
    public Detail restore(Long id) {
        Institution i = find(id);
        if (i.getLifecycle() == Lifecycle.ARCHIVED) {
            boolean complete = i.getGradingSystem() != null && i.getPlanType() != null && i.getEmail() != null;
            i.setLifecycle(complete ? Lifecycle.ACTIVE : Lifecycle.DRAFT);
            i.setArchivedAt(null);
            audit.record("INSTITUTION_RESTORED", "INSTITUTION", i.getId(), i.getId(), "Institution \"" + i.getName() + "\" was restored");
        }
        return toDetail(repo.save(i));
    }

    /** Irreversible. Only for archived institutions, and only when nothing else still depends on them. */
    @Transactional
    public void deletePermanently(Long id) {
        Institution i = find(id);
        if (i.getLifecycle() != Lifecycle.ARCHIVED) {
            throw ApiException.conflict("Archive the institution first; only archived institutions can be deleted permanently.");
        }
        try {
            repo.delete(i);
            repo.flush();
            audit.record("INSTITUTION_DELETED", "INSTITUTION", id, null, "Institution \"" + i.getName() + "\" was deleted permanently");
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("This institution still has users, academic data or students, so it can't be deleted permanently. Keep it archived instead.");
        }
    }

    // --------------------------------------------------------------- images

    @Transactional
    public void saveImage(Long id, ImageKind kind, MultipartFile file) {
        Institution i = find(id);
        if (i.getLifecycle() == Lifecycle.ARCHIVED) {
            throw ApiException.conflict("This institution is archived. Restore it before changing images.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose an image file.");
        }
        if (file.getSize() > kind.maxBytes) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "That image is too large (max " + kind.maxBytes / (1024 * 1024) + " MB).");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("The image could not be read.");
        }
        String contentType = sniff(data);
        if (contentType == null) {
            throw ApiException.badRequest("Only PNG, JPG or WEBP images are allowed.");
        }
        var existing = images.find(id, kind);
        if (existing.isPresent()) {
            existing.get().replace(contentType, data);
            images.save(existing.get());
        } else {
            images.save(new InstitutionImage(id, kind, contentType, data));
        }
        i.touch();
        repo.save(i);
        audit.record("INSTITUTION_IMAGE", "INSTITUTION", id, id, "The " + kind.name().toLowerCase(Locale.ROOT) + " of \"" + i.getName() + "\" was changed");
    }

    @Transactional
    public void deleteImage(Long id, ImageKind kind) {
        Institution i = find(id);
        images.find(id, kind).ifPresent(images::delete);
        i.touch();
        repo.save(i);
    }

    /** Used by the public image endpoint. Archived institutions show no branding. */
    @Transactional(readOnly = true)
    public InstitutionImage image(Long id, ImageKind kind) {
        Institution i = find(id);
        if (i.getLifecycle() == Lifecycle.ARCHIVED) {
            throw ApiException.notFound("Image");
        }
        return images.find(id, kind).orElseThrow(() -> ApiException.notFound("Image"));
    }

    /** Decides the type from the file's first bytes, never from the name or the browser's claim. */
    static String sniff(byte[] d) {
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    // -------------------------------------------------------------- helpers

    private Institution find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Institution"));
    }

    private void validate(Input in, boolean draft, String currentType) {
        Map<String, String> e = new LinkedHashMap<>();
        if (in.institutionType() != null && !in.institutionType().isBlank()) {
            String code = InstitutionKindService.codeOf(in.institutionType());
            InstitutionKind kind = kinds.findByCode(code).orElse(null);
            if (kind == null) {
                e.put("institutionType", "is not a known institution type");
            } else if (!kind.isActive() && !code.equals(currentType)) {
                e.put("institutionType", "\"" + kind.getName() + "\" is inactive. Choose another type");
            }
        }
        String email = blankToNull(in.email());
        String altEmail = blankToNull(in.altEmail());
        // Format checks always apply, even for drafts.
        if (email != null && !EMAIL.matcher(email).matches()) e.put("email", "must be a valid email address");
        if (altEmail != null && !EMAIL.matcher(altEmail).matches()) e.put("altEmail", "must be a valid email address");
        String phone = blankToNull(in.phone());
        String altPhone = blankToNull(in.altPhone());
        if (phone != null && !PHONE.matcher(phone).matches()) e.put("phone", "must be 6-15 digits");
        if (altPhone != null && !PHONE.matcher(altPhone).matches()) e.put("altPhone", "must be 6-15 digits");
        if (in.academicStartMonth() != null && (in.academicStartMonth() < 1 || in.academicStartMonth() > 12)) {
            e.put("academicStartMonth", "must be between 1 and 12");
        }
        if (in.establishmentYear() != null
                && (in.establishmentYear() < 1800 || in.establishmentYear() > LocalDate.now().getYear())) {
            e.put("establishmentYear", "is not a valid year");
        }
        if (in.startTime() != null && in.endTime() != null && !in.endTime().isAfter(in.startTime())) {
            e.put("endTime", "must be after the start time");
        }
        if (in.subscriptionStart() != null && in.subscriptionEnd() != null
                && in.subscriptionEnd().isBefore(in.subscriptionStart())) {
            e.put("subscriptionEnd", "must be on or after the start date");
        }
        if (!draft) {
            require(e, "institutionType", in.institutionType() != null, "is required");
            require(e, "contactName", blankToNull(in.contactName()) != null, "is required");
            require(e, "designation", blankToNull(in.designation()) != null, "is required");
            require(e, "email", email != null, "is required");
            require(e, "phone", phone != null, "is required");
            require(e, "address", blankToNull(in.address()) != null, "is required");
            require(e, "state", blankToNull(in.state()) != null, "is required");
            require(e, "city", blankToNull(in.city()) != null, "is required");
            require(e, "pinCode", blankToNull(in.pinCode()) != null, "is required");
            require(e, "gradingSystem", in.gradingSystem() != null, "is required");
            require(e, "planType", in.planType() != null, "is required");
            require(e, "subscriptionStart", in.subscriptionStart() != null, "is required");
            require(e, "subscriptionEnd", in.subscriptionEnd() != null, "is required");
            require(e, "expectedStudents", in.expectedStudents() != null && in.expectedStudents() >= 1, "must be at least 1");
            require(e, "expectedTeachers", in.expectedTeachers() != null && in.expectedTeachers() >= 1, "must be at least 1");
            require(e, "workingDays", in.workingDays() != null && !in.workingDays().isEmpty(), "needs at least one day");
        }
        if (!e.isEmpty()) {
            throw new FieldErrorsException(e);
        }
    }

    private static void require(Map<String, String> errors, String field, boolean ok, String message) {
        if (!ok) errors.putIfAbsent(field, message);
    }

    private void apply(Institution i, Input in) {
        i.setName(in.name().trim());
        if (in.institutionType() != null && !in.institutionType().isBlank()) {
            i.setInstitutionType(InstitutionKindService.codeOf(in.institutionType()));
        }
        i.setAffiliation(blankToNull(in.affiliation()));
        i.setEstablishmentYear(in.establishmentYear());
        i.setDescription(blankToNull(in.description()));
        i.setContactName(blankToNull(in.contactName()));
        i.setDesignation(blankToNull(in.designation()));
        i.setEmail(lower(blankToNull(in.email())));
        i.setPhoneCode(blankToNull(in.phoneCode()));
        i.setPhone(blankToNull(in.phone()));
        i.setAltContactName(blankToNull(in.altContactName()));
        i.setAltDesignation(blankToNull(in.altDesignation()));
        i.setAltEmail(lower(blankToNull(in.altEmail())));
        i.setAltPhoneCode(blankToNull(in.altPhoneCode()));
        i.setAltPhone(blankToNull(in.altPhone()));
        i.setAddress(blankToNull(in.address()));
        if (blankToNull(in.country()) != null) i.setCountry(in.country().trim());
        i.setState(blankToNull(in.state()));
        i.setCity(blankToNull(in.city()));
        i.setPinCode(blankToNull(in.pinCode()));
        if (in.academicStartMonth() != null) i.setAcademicStartMonth(in.academicStartMonth());
        if (blankToNull(in.dateFormat()) != null) i.setDateFormat(in.dateFormat().trim());
        if (blankToNull(in.timeZone()) != null) i.setTimeZone(in.timeZone().trim());
        if (blankToNull(in.language()) != null) i.setLanguage(in.language().trim());
        i.setGradingSystem(in.gradingSystem());
        if (blankToNull(in.currency()) != null) i.setCurrency(in.currency().trim());
        if (in.startTime() != null) i.setStartTime(in.startTime());
        if (in.endTime() != null) i.setEndTime(in.endTime());
        i.setPlanType(in.planType());
        i.setSubscriptionStart(in.subscriptionStart());
        i.setSubscriptionEnd(in.subscriptionEnd());
        i.setExpectedStudents(in.expectedStudents());
        i.setExpectedTeachers(in.expectedTeachers());

        i.getModules().clear();
        if (in.modules() != null) i.getModules().addAll(in.modules());
        i.getWorkingDays().clear();
        if (in.workingDays() != null) i.getWorkingDays().addAll(in.workingDays());
    }

    public static Status statusOf(Institution i) {
        if (i.getLifecycle() == Lifecycle.ARCHIVED) return Status.ARCHIVED;
        if (i.getLifecycle() == Lifecycle.DRAFT) return Status.DRAFT;
        LocalDate end = i.getSubscriptionEnd();
        if (end != null) {
            LocalDate today = LocalDate.now();
            if (end.isBefore(today)) return Status.EXPIRED;
            if (i.getPlanType() == PlanType.TRIAL) return Status.TRIAL;
            if (ChronoUnit.DAYS.between(today, end) <= 30) return Status.EXPIRING_SOON;
        } else if (i.getPlanType() == PlanType.TRIAL) {
            return Status.TRIAL;
        }
        return Status.ACTIVE;
    }

    private static boolean matchesStatus(Status s, String filter) {
        if (filter == null || filter.isBlank()) return s != Status.ARCHIVED;
        if (filter.equalsIgnoreCase("ALL")) return true;
        return s.name().equalsIgnoreCase(filter.trim());
    }

    private Detail toDetail(Institution i) {
        long studentCount = toMap(students.countGroupedByInstitution()).getOrDefault(i.getId(), 0L);
        long teacherCount = toMap(teachers.countGroupedByInstitution()).getOrDefault(i.getId(), 0L);
        return new Detail(i.getId(), statusOf(i), i.getLifecycle(), i.getName(), i.getInstitutionType(),
                i.getAffiliation(), i.getEstablishmentYear(), i.getDescription(), i.getContactName(),
                i.getDesignation(), i.getEmail(), i.getPhoneCode(), i.getPhone(), i.getAltContactName(),
                i.getAltDesignation(), i.getAltEmail(), i.getAltPhoneCode(), i.getAltPhone(), i.getAddress(),
                i.getCountry(), i.getState(), i.getCity(), i.getPinCode(), i.getAcademicStartMonth(),
                i.getDateFormat(), i.getTimeZone(), i.getLanguage(), i.getGradingSystem(), i.getCurrency(),
                new java.util.TreeSet<>(i.getWorkingDays()), i.getStartTime(), i.getEndTime(), i.getPlanType(),
                i.getSubscriptionStart(), i.getSubscriptionEnd(), i.getExpectedStudents(), i.getExpectedTeachers(),
                new java.util.TreeSet<>(i.getModules()),
                images.existsFor(i.getId(), ImageKind.LOGO), images.existsFor(i.getId(), ImageKind.BANNER),
                studentCount, teacherCount, i.getCreatedAt(), i.getUpdatedAt());
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        return m;
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
