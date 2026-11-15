package com.centram.classzy.transport;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface TransportDriverRepository extends TenantRepository<TransportDriver> {
    List<TransportDriver> findByInstitutionIdOrderByNameAsc(Long institutionId);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(Long institutionId, String code, Long id);

    boolean existsByInstitutionIdAndLicenseNoIgnoreCaseAndIdNot(Long institutionId, String licenseNo, Long id);

    long countByInstitutionId(Long institutionId);
}
