package com.centram.classzy.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TimetableSettingsRepository extends JpaRepository<TimetableSettings, Long> {
    /** Creates the row if missing. Returns 1 only for the caller that created it. */
    @Modifying
    @Query(value = "insert into timetable_settings (institution_id) values (:institutionId) on conflict do nothing", nativeQuery = true)
    int createIfMissing(Long institutionId);
}
