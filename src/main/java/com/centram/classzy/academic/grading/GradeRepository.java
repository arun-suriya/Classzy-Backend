package com.centram.classzy.academic.grading;

import java.util.List;
import java.util.Optional;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeRepository extends TenantRepository<Grade> {

    List<Grade> findByInstitutionIdOrderByMarksFromDesc(Long institutionId);

    Optional<Grade> findByIdAndGradingSystemIdAndInstitutionId(Long id, Long gradingSystemId, Long institutionId);

    boolean existsByGradingSystemIdAndGradeIgnoreCaseAndIdNot(Long gradingSystemId, String grade, Long id);

    @Query("select count(g) > 0 from Grade g where g.gradingSystemId = :system and g.id <> :id "
            + "and g.marksFrom <= :to and g.marksTo >= :from")
    boolean overlaps(@Param("system") Long system, @Param("id") Long id,
                     @Param("from") Integer from, @Param("to") Integer to);
}
