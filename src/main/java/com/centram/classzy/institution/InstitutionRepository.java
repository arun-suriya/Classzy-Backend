package com.centram.classzy.institution;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    List<Institution> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    long countByInstitutionType(String institutionType);

    /** Institutions per type code, archived ones excluded. */
    @org.springframework.data.jpa.repository.Query(
            "select i.institutionType, count(i) from Institution i where i.lifecycle <> com.centram.classzy.institution.InstitutionEnums.Lifecycle.ARCHIVED group by i.institutionType")
    List<Object[]> countGroupedByType();

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
