package com.centram.classzy.academic.term;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TermRepository extends TenantRepository<Term> {

    List<Term> findByInstitutionIdAndAcademicYearIdOrderByStartDateAsc(Long institutionId, Long academicYearId);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(Long academicYearId, String name, Long id);

    @Query("select count(t) > 0 from Term t where t.academicYearId = :year and t.id <> :id "
            + "and t.startDate <= :end and t.endDate >= :start")
    boolean overlaps(@Param("year") Long year, @Param("id") Long id,
                     @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Sum of weightage of the other active terms in the year (null when there are none). */
    @Query("select sum(t.weightage) from Term t where t.academicYearId = :year and t.active = true and t.id <> :id")
    BigDecimal sumActiveWeightage(@Param("year") Long year, @Param("id") Long id);
}
