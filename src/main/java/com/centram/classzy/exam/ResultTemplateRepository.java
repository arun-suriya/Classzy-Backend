package com.centram.classzy.exam;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ResultTemplateRepository extends TenantRepository<ResultTemplate> {
    List<ResultTemplate> findByInstitutionIdAndAcademicYearIdOrderByIdAsc(Long institutionId, Long academicYearId);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(Long academicYearId, String name, Long id);

    @Query("select count(r) > 0 from ResultTemplate r join r.examTypeIds e where e = :examTypeId")
    boolean usesExamType(Long examTypeId);
}
