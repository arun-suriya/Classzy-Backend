package com.centram.classzy.academic.term;

import java.math.BigDecimal;
import java.util.List;

import com.centram.classzy.academic.term.TermDto.Request;
import com.centram.classzy.academic.year.AcademicYear;
import com.centram.classzy.academic.year.AcademicYearService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TermRepository repo;
    private final AcademicYearService years;
    private final CurrentUser current;

    public TermService(TermRepository repo, AcademicYearService years, CurrentUser current) {
        this.repo = repo;
        this.years = years;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<Term> list(Long academicYearId) {
        years.require(academicYearId);
        return repo.findByInstitutionIdAndAcademicYearIdOrderByStartDateAsc(current.institutionId(), academicYearId);
    }

    @Transactional
    public Term create(Request r) {
        var t = new Term();
        t.setInstitutionId(current.institutionId());
        return save(t, r);
    }

    @Transactional
    public Term update(Long id, Request r) {
        return save(require(id), r);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(require(id));
        repo.flush();
    }

    private Term require(Long id) {
        return repo.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Term"));
    }

    private Term save(Term t, Request r) {
        AcademicYear year = years.require(r.academicYearId());
        long id = t.getId() == null ? -1L : t.getId();
        String name = r.name().trim();

        if (!r.endDate().isAfter(r.startDate())) {
            throw ApiException.badRequest("Term End Date must be after the Start Date.");
        }
        if (r.startDate().isBefore(year.getStartDate()) || r.endDate().isAfter(year.getEndDate())) {
            throw ApiException.badRequest("Term dates must fall within the academic year " + year.getName() + ".");
        }
        if (repo.existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(year.getId(), name, id)) {
            throw ApiException.conflict("A term named " + name + " already exists in this academic year.");
        }
        if (repo.overlaps(year.getId(), id, r.startDate(), r.endDate())) {
            throw ApiException.conflict("These dates overlap with another term in this academic year.");
        }
        if (r.active()) {
            BigDecimal others = repo.sumActiveWeightage(year.getId(), id);
            BigDecimal used = others == null ? BigDecimal.ZERO : others;
            if (used.add(r.weightage()).compareTo(HUNDRED) > 0) {
                throw ApiException.badRequest("Total weightage of active terms can't exceed 100% ("
                        + used.stripTrailingZeros().toPlainString() + "% is already used by other terms).");
            }
        }

        t.setAcademicYearId(year.getId());
        t.setName(name);
        t.setStartDate(r.startDate());
        t.setEndDate(r.endDate());
        t.setWeightage(r.weightage());
        t.setActive(r.active());
        t.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        return repo.saveAndFlush(t);
    }
}
