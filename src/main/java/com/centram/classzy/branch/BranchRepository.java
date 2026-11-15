package com.centram.classzy.branch;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByInstitutionId(Long institutionId);

    long countByInstitutionId(Long institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCase(Long institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(Long institutionId, String name, Long id);

    boolean existsByInstitutionIdAndCodeIgnoreCase(Long institutionId, String code);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(Long institutionId, String code, Long id);

    /** Makes room for a new main branch (only one per institution). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Branch b set b.mainBranch = false where b.institutionId = :institutionId and b.id <> :exceptId and b.mainBranch = true")
    int clearMain(Long institutionId, Long exceptId);
}
