package com.centram.classzy.academic.subject;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.centram.classzy.academic.subject.SubjectDto.Request;
import com.centram.classzy.academic.year.AcademicYearService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectService {

    static final Set<String> CATEGORIES = Set.of("Language", "Core", "Skill", "Co-Curricular");

    private final SubjectRepository repo;
    private final AcademicYearService years;
    private final CurrentUser current;

    public SubjectService(SubjectRepository repo, AcademicYearService years, CurrentUser current) {
        this.repo = repo;
        this.years = years;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<Subject> list(Long academicYearId) {
        years.require(academicYearId);
        return repo.findByInstitutionIdAndAcademicYearIdOrderByNameAsc(current.institutionId(), academicYearId);
    }

    @Transactional
    public Subject create(Request r) {
        var s = new Subject();
        s.setInstitutionId(current.institutionId());
        return save(s, r);
    }

    @Transactional
    public Subject update(Long id, Request r) {
        return save(require(id), r);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(require(id));
        repo.flush();
    }

    private Subject require(Long id) {
        return repo.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Subject"));
    }

    private Subject save(Subject s, Request r) {
        years.require(r.academicYearId());
        long id = s.getId() == null ? -1L : s.getId();
        String code = r.code().trim().toUpperCase(Locale.ROOT);

        if (!CATEGORIES.contains(r.category())) {
            throw ApiException.badRequest("Category must be one of " + String.join(", ", CATEGORIES) + ".");
        }
        if (r.classLevels().stream().anyMatch(l -> l == null || l < 1 || l > 12)) {
            throw ApiException.badRequest("Applicable classes must be between 1 and 12.");
        }
        if (repo.existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(r.academicYearId(), code, id)) {
            throw ApiException.conflict("Subject code " + code + " is already in use for this academic year.");
        }

        s.setAcademicYearId(r.academicYearId());
        s.setName(r.name().trim());
        s.setCode(code);
        s.setCategory(r.category());
        s.setActive(r.active());
        s.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        s.getGradeLevels().clear();
        s.getGradeLevels().addAll(r.classLevels());
        return repo.saveAndFlush(s);
    }
}
