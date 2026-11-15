package com.centram.classzy.communication;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface CommTemplateRepository extends TenantRepository<CommTemplate> {
    List<CommTemplate> findByInstitutionIdOrderByChannelAscNameAsc(Long institutionId);

    List<CommTemplate> findByInstitutionIdAndChannelOrderByNameAsc(Long institutionId, String channel);

    boolean existsByInstitutionIdAndChannelAndNameIgnoreCaseAndIdNot(Long institutionId, String channel, String name, Long id);
}
