package com.centram.classzy.people;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

public interface TeacherRepository extends TenantRepository<Teacher> {
    @org.springframework.data.jpa.repository.Query("select t.institutionId, count(t) from Teacher t group by t.institutionId")
    List<Object[]> countGroupedByInstitution();

    List<Teacher> findByInstitutionIdOrderByFullNameAsc(Long institutionId);
}
