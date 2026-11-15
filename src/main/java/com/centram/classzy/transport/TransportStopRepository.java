package com.centram.classzy.transport;

import java.util.List;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransportStopRepository extends TenantRepository<TransportStop> {
    List<TransportStop> findByInstitutionIdAndRouteIdOrderBySequenceNoAscIdAsc(Long institutionId, Long routeId);

    boolean existsByRouteIdAndNameIgnoreCaseAndIdNot(Long routeId, String name, Long id);

    @Query("select s.routeId, count(s) from TransportStop s where s.institutionId = :institutionId group by s.routeId")
    List<Object[]> countByRoute(Long institutionId);
}
