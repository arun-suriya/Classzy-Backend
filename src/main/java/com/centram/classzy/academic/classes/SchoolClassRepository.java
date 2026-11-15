package com.centram.classzy.academic.classes;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

public interface SchoolClassRepository extends TenantRepository<SchoolClass> {

    List<SchoolClass> findByInstitutionIdAndAcademicYearIdOrderByGradeLevelAscNameAsc(Long institutionId, Long academicYearId);

    boolean existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(Long academicYearId, String name, Long id);
}
