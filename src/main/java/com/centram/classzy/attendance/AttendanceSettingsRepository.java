package com.centram.classzy.attendance;

import java.util.List;
import java.util.Optional;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceSettingsRepository extends JpaRepository<AttendanceSettings, Long> {
    /** Creates the settings row if it does not exist yet. Returns 1 only for the caller that created it. */
    @Modifying
    @Query(value = "insert into attendance_settings (institution_id) values (:institutionId) on conflict do nothing", nativeQuery = true)
    int createIfMissing(Long institutionId);
}
