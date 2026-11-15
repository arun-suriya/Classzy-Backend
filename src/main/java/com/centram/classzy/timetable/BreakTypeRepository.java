package com.centram.classzy.timetable;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface BreakTypeRepository extends TenantRepository<BreakType> {
    List<BreakType> findByInstitutionIdOrderByIdAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(Long institutionId, String code, Long id);
}
