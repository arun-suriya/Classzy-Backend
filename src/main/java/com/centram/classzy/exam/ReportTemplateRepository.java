package com.centram.classzy.exam;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface ReportTemplateRepository extends TenantRepository<ReportTemplate> {
    List<ReportTemplate> findByInstitutionIdAndAcademicYearIdOrderByIdAsc(Long institutionId, Long academicYearId);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(Long academicYearId, String name, Long id);
}
