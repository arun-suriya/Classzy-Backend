package com.centram.classzy.institutiontype;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionKindRepository extends JpaRepository<InstitutionKind, Long> {

    List<InstitutionKind> findAllByOrderByNameAsc();

    Optional<InstitutionKind> findByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByCode(String code);
}
