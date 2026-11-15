package com.centram.classzy.timetable;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface TimetableSlotRepository extends TenantRepository<TimetableSlot> {
    List<TimetableSlot> findByInstitutionIdOrderByStartTimeAsc(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);
}
