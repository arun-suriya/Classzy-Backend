package com.centram.classzy.common;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Always look records up together with the institution id, so one institution can never read another's data. */
@NoRepositoryBean
public interface TenantRepository<T extends TenantEntity> extends JpaRepository<T, Long> {
    Optional<T> findByIdAndInstitutionId(Long id, Long institutionId);
}
