package com.centram.classzy.communication;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface PushCategoryRepository extends TenantRepository<PushCategory> {
    List<PushCategory> findByInstitutionIdOrderByIdAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);
}
