package com.centram.classzy.academic.grading;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

public interface GradingSystemRepository extends TenantRepository<GradingSystem> {
    List<GradingSystem> findByInstitutionIdOrderByNameAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);
}
