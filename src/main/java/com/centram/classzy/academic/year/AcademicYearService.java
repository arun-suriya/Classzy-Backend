package com.centram.classzy.academic.year;

import java.util.List;

import com.centram.classzy.academic.year.YearDto.Request;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicYearService {

    private final AcademicYearRepository repo;
    private final CurrentUser current;

    public AcademicYearService(AcademicYearRepository repo, CurrentUser current) {
        this.repo = repo;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<AcademicYear> list() {
        return repo.findByInstitutionIdOrderByStartDateDesc(current.institutionId());
    }

    /** Loads a year of the current institution or fails with 404. Other modules use this to validate yearId. */
    @Transactional(readOnly = true)
    public AcademicYear require(Long id) {
        return repo.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Academic year"));
    }

    @Transactional
    public AcademicYear create(Request r) {
        var y = new AcademicYear();
        y.setInstitutionId(current.institutionId());
        return save(y, r);
    }

    @Transactional
    public AcademicYear update(Long id, Request r) {
        return save(require(id), r);
    }

    @Transactional
    public void delete(Long id) {
        AcademicYear y = require(id);
        if (y.isCurrentYear()) {
            throw ApiException.conflict("The current academic year can't be deleted. Mark another year as current first.");
        }
        repo.delete(y);
        repo.flush();   // surfaces "still in use" (classes/subjects) as a 409 right here
    }

    private AcademicYear save(AcademicYear y, Request r) {
        Long inst = current.institutionId();
        long id = y.getId() == null ? -1L : y.getId();
        String name = r.name().trim();

        if (!r.endDate().isAfter(r.startDate())) {
            throw ApiException.badRequest("End Date must be after the Start Date.");
        }
        if (repo.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, id)) {
            throw ApiException.conflict("An academic year named " + name + " already exists.");
        }
        if (repo.overlaps(inst, id, r.startDate(), r.endDate())) {
            throw ApiException.conflict("These dates overlap with another academic year.");
        }
        if (r.current()) {
            repo.clearCurrent(inst);          // only one current year per institution
        }

        y.setName(name);
        y.setShortName(blankToNull(r.shortName()));
        y.setStartDate(r.startDate());
        y.setEndDate(r.endDate());
        y.setCurrentYear(r.current());
        y.setActive(r.active());
        y.setDescription(blankToNull(r.description()));
        return repo.saveAndFlush(y);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
