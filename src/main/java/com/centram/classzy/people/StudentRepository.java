package com.centram.classzy.people;

import java.util.Collection;
import java.util.List;

import com.centram.classzy.common.TenantRepository;

public interface StudentRepository extends TenantRepository<Student> {

    List<Student> findByInstitutionIdOrderByFullNameAsc(Long institutionId);

    List<Student> findByInstitutionIdAndSchoolClassIdOrderByFullNameAsc(Long institutionId, Long schoolClassId);

    List<Student> findByInstitutionIdAndSchoolClassIdIn(Long institutionId, Collection<Long> schoolClassIds);

    @org.springframework.data.jpa.repository.Query("select s.institutionId, count(s) from Student s group by s.institutionId")
    List<Object[]> countGroupedByInstitution();

    long countBySchoolClassId(Long schoolClassId);

    long countBySectionId(Long sectionId);

    boolean existsByInstitutionIdAndAdmissionNoIgnoreCaseAndIdNot(Long institutionId, String admissionNo, Long id);
}
