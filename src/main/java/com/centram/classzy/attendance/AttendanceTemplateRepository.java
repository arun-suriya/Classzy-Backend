package com.centram.classzy.attendance;

import java.util.List;
import java.util.Optional;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceTemplateRepository extends TenantRepository<AttendanceTemplate> {
    List<AttendanceTemplate> findByInstitutionIdOrderByIdAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);
}
