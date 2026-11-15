package com.centram.classzy.exam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ExamSettingsRepository extends JpaRepository<ExamSettings, Long> {
    @Modifying
    @Query(value = "insert into exam_settings (institution_id) values (:institutionId) on conflict do nothing", nativeQuery = true)
    int createIfMissing(Long institutionId);
}
