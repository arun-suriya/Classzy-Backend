package com.centram.classzy.timetable;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface BreakRuleRepository extends TenantRepository<BreakRule> {
    List<BreakRule> findByInstitutionIdOrderByStartTimeAscIdAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

    boolean existsByInstitutionIdAndBreakTypeId(Long institutionId, Long breakTypeId);
}
