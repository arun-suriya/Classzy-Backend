package com.centram.classzy.attendance;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** All the attendance repositories in one place. */
public final class AttendanceRepositories {
    private AttendanceRepositories() {
    }

    @Repository
    public interface Settings extends JpaRepository<AttendanceSettings, Long> {
        /** Creates the settings row if it does not exist yet. Returns 1 only for the caller that created it. */
        @Modifying
        @Query(value = "insert into attendance_settings (institution_id) values (:institutionId) on conflict do nothing", nativeQuery = true)
        int createIfMissing(Long institutionId);
    }

    @Repository
    public interface Types extends TenantRepository<AttendanceType> {
        List<AttendanceType> findByInstitutionIdOrderBySortOrderAscIdAsc(Long institutionId);

        boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

        boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(Long institutionId, String code, Long id);

        java.util.Optional<AttendanceType> findByInstitutionIdAndCodeIgnoreCase(Long institutionId, String code);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
        @Query("update AttendanceType t set t.defaultType = false where t.institutionId = :institutionId and t.id <> :exceptId and t.defaultType = true")
        int clearDefault(Long institutionId, Long exceptId);
    }

    @Repository
    public interface Rules extends TenantRepository<AttendanceRule> {
        List<AttendanceRule> findByInstitutionIdOrderByPriorityAscIdAsc(Long institutionId);

        boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

        boolean existsByInstitutionIdAndActionTypeCodeIgnoreCase(Long institutionId, String code);
    }

    @Repository
    public interface Templates extends TenantRepository<AttendanceTemplate> {
        List<AttendanceTemplate> findByInstitutionIdOrderByIdAsc(Long institutionId);

        boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);
    }

    @Repository
    public interface Events extends TenantRepository<AttendanceNotificationEvent> {
        List<AttendanceNotificationEvent> findByInstitutionIdOrderByIdAsc(Long institutionId);

        boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

        boolean existsByInstitutionIdAndTemplateId(Long institutionId, Long templateId);
    }
}
