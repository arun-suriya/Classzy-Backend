package com.centram.classzy.system;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    Page<HealthLog> findAllByOrderByCheckedAtDesc(Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from HealthLog h where h.checkedAt < :before")
    int deleteOlderThan(Instant before);
}
