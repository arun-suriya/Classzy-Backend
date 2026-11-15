package com.centram.classzy.academic.promotion;

import java.util.List;
import java.util.Optional;

import com.centram.classzy.common.TenantRepository;

public interface StudentProgressionRepository extends TenantRepository<StudentProgression> {

    List<StudentProgression> findByInstitutionIdAndAcademicYearId(Long institutionId, Long academicYearId);

    Optional<StudentProgression> findByAcademicYearIdAndStudentId(Long academicYearId, Long studentId);
}
