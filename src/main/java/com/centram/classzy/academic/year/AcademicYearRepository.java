package com.centram.classzy.academic.year;

import java.time.LocalDate;
import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicYearRepository extends TenantRepository<AcademicYear> {

    List<AcademicYear> findByInstitutionIdOrderByStartDateDesc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

    @Query("select count(y) > 0 from AcademicYear y where y.institutionId = :inst and y.id <> :id "
            + "and y.startDate <= :end and y.endDate >= :start")
    boolean overlaps(@Param("inst") Long inst, @Param("id") Long id,
                     @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AcademicYear y set y.currentYear = false where y.institutionId = :inst")
    void clearCurrent(@Param("inst") Long inst);
}
