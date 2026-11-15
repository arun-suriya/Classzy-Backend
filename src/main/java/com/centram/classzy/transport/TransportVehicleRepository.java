package com.centram.classzy.transport;

import java.util.List;
import java.util.Optional;

import com.centram.classzy.common.TenantRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransportVehicleRepository extends TenantRepository<TransportVehicle> {
    List<TransportVehicle> findByInstitutionIdOrderByVehicleNoAsc(Long institutionId);

    boolean existsByInstitutionIdAndVehicleNoIgnoreCaseAndIdNot(Long institutionId, String vehicleNo, Long id);

    boolean existsByInstitutionIdAndRegistrationNoIgnoreCaseAndIdNot(Long institutionId, String registrationNo, Long id);

    Optional<TransportVehicle> findFirstByInstitutionIdAndDriverId(Long institutionId, Long driverId);

    @Query("select v.routeId, count(v) from TransportVehicle v where v.institutionId = :institutionId and v.routeId is not null group by v.routeId")
    List<Object[]> countByRoute(Long institutionId);
}
