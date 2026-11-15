package com.centram.classzy.exam;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface ExamTypeRepository extends TenantRepository<ExamType> {
    List<ExamType> findByInstitutionIdAndAcademicYearIdOrderBySortOrderAscIdAsc(Long institutionId, Long academicYearId);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(Long academicYearId, String name, Long id);

    boolean existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(Long academicYearId, String code, Long id);
}
