package com.centram.classzy.timetable;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface TimetableHolidayRepository extends TenantRepository<TimetableHoliday> {
    List<TimetableHoliday> findByInstitutionIdOrderByStartDateAsc(Long institutionId);
}
