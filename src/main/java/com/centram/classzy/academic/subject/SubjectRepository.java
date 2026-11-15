package com.centram.classzy.academic.subject;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

public interface SubjectRepository extends TenantRepository<Subject> {

    List<Subject> findByInstitutionIdAndAcademicYearIdOrderByNameAsc(Long institutionId, Long academicYearId);

    boolean existsByAcademicYearIdAndCodeIgnoreCaseAndIdNot(Long academicYearId, String code, Long id);
}
