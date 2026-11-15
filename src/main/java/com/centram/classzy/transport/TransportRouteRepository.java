package com.centram.classzy.transport;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface TransportRouteRepository extends TenantRepository<TransportRoute> {
    List<TransportRoute> findByInstitutionIdOrderByNameAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(Long institutionId, String code, Long id);
}
