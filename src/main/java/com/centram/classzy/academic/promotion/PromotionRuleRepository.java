package com.centram.classzy.academic.promotion;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

public interface PromotionRuleRepository extends TenantRepository<PromotionRule> {

    List<PromotionRule> findByInstitutionIdAndAcademicYearIdOrderByIdAsc(Long institutionId, Long academicYearId);

    List<PromotionRule> findByInstitutionIdAndAcademicYearIdAndActiveTrueOrderByIdAsc(Long institutionId, Long academicYearId);
}
