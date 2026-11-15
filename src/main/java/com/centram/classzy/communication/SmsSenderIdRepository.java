package com.centram.classzy.communication;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SmsSenderIdRepository extends TenantRepository<SmsSenderId> {
    List<SmsSenderId> findByInstitutionIdOrderByIdAsc(Long institutionId);

    boolean existsByInstitutionIdAndSenderIdIgnoreCaseAndIdNot(Long institutionId, String senderId, Long id);

    @Modifying
    @Query("update SmsSenderId s set s.defaultSender = false where s.institutionId = :institutionId and s.id <> :keepId")
    void clearDefaultExcept(Long institutionId, Long keepId);
}
