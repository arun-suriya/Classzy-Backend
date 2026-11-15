package com.centram.classzy.academic.classes;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

public interface SectionRepository extends TenantRepository<Section> {

    List<Section> findByInstitutionIdAndSchoolClassIdOrderByNameAsc(Long institutionId, Long schoolClassId);

    long countBySchoolClassId(Long schoolClassId);

    boolean existsBySchoolClassIdAndNameIgnoreCaseAndIdNot(Long schoolClassId, String name, Long id);
}
