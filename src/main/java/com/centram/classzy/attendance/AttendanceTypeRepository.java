package com.centram.classzy.attendance;

import java.util.List;
import java.util.Optional;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceTypeRepository extends TenantRepository<AttendanceType> {
    List<AttendanceType> findByInstitutionIdOrderBySortOrderAscIdAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(Long institutionId, String code, Long id);

    Optional<AttendanceType> findByInstitutionIdAndCodeIgnoreCase(Long institutionId, String code);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AttendanceType t set t.defaultType = false where t.institutionId = :institutionId and t.id <> :exceptId and t.defaultType = true")
    int clearDefault(Long institutionId, Long exceptId);
}
