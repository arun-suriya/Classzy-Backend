package com.centram.classzy.transport;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.transport.TransportDto.DriverBody;
import com.centram.classzy.transport.TransportDto.DriverView;
import com.centram.classzy.transport.TransportDto.VehicleBody;
import com.centram.classzy.transport.TransportDto.VehicleView;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vehicles and drivers. A vehicle can have one route and one driver; a driver can drive one vehicle. */
@Service
@Transactional
public class FleetService {

    private static final Set<String> VEHICLE_TYPES = Set.of("BUS", "VAN", "CAR", "OTHER");
    private static final Set<String> FUEL_TYPES = Set.of("DIESEL", "PETROL", "CNG", "ELECTRIC", "OTHER");
    private static final Set<String> VEHICLE_STATES = Set.of("ACTIVE", "INACTIVE", "MAINTENANCE");
    private static final Set<String> DRIVER_STATES = Set.of("ACTIVE", "ON_LEAVE", "INACTIVE");
    private static final Set<String> LICENSE_TYPES = Set.of("LMV", "HMV", "HGMV", "HPMV");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");

    private final CurrentUser current;
    private final TransportVehicleRepository vehicles;
    private final TransportDriverRepository drivers;
    private final TransportRouteRepository routes;
    private final AuditService audit;

    public FleetService(CurrentUser current, TransportVehicleRepository vehicles, TransportDriverRepository drivers, TransportRouteRepository routes, AuditService audit) {
        this.current = current;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.routes = routes;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ vehicles

    @Transactional(readOnly = true)
    public List<VehicleView> listVehicles() {
        Long inst = current.institutionId();
        Map<Long, String> routeNames = routes.findByInstitutionIdOrderByNameAsc(inst).stream().collect(Collectors.toMap(TransportRoute::getId, r -> r.name));
        Map<Long, String> driverNames = drivers.findByInstitutionIdOrderByNameAsc(inst).stream().collect(Collectors.toMap(TransportDriver::getId, d -> d.name));
        return vehicles.findByInstitutionIdOrderByVehicleNoAsc(inst).stream()
                .map(v -> VehicleView.of(v, v.routeId == null ? null : routeNames.get(v.routeId), v.driverId == null ? null : driverNames.get(v.driverId))).toList();
    }

    public VehicleView createVehicle(VehicleBody b) {
        Long inst = current.institutionId();
        TransportVehicle v = new TransportVehicle();
        v.setInstitutionId(inst);
        applyVehicle(v, b, inst, 0L);
        v = vehicles.saveAndFlush(v);
        audit.record("TRANSPORT_VEHICLE_CREATED", "TransportVehicle", v.getId(), inst, "Vehicle " + v.vehicleNo + " added");
        return view(v, inst);
    }

    public VehicleView updateVehicle(Long id, VehicleBody b) {
        Long inst = current.institutionId();
        TransportVehicle v = vehicles.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Vehicle"));
        applyVehicle(v, b, inst, id);
        v = vehicles.saveAndFlush(v);
        audit.record("TRANSPORT_VEHICLE_UPDATED", "TransportVehicle", id, inst, "Vehicle " + v.vehicleNo + " updated");
        return view(v, inst);
    }

    public void deleteVehicle(Long id) {
        Long inst = current.institutionId();
        TransportVehicle v = vehicles.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Vehicle"));
        vehicles.delete(v);
        audit.record("TRANSPORT_VEHICLE_DELETED", "TransportVehicle", id, inst, "Vehicle " + v.vehicleNo + " deleted");
    }

    private VehicleView view(TransportVehicle v, Long inst) {
        String route = v.routeId == null ? null : routes.findByIdAndInstitutionId(v.routeId, inst).map(r -> r.name).orElse(null);
        String driver = v.driverId == null ? null : drivers.findByIdAndInstitutionId(v.driverId, inst).map(d -> d.name).orElse(null);
        return VehicleView.of(v, route, driver);
    }

    private void applyVehicle(TransportVehicle v, VehicleBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String no = Check.trim(b.vehicleNo());
        String reg = Check.trim(b.registrationNo());
        if (no.isEmpty()) err.put("vehicleNo", "Vehicle number is required.");
        else if (no.length() > 20) err.put("vehicleNo", "At most 20 characters.");
        else if (vehicles.existsByInstitutionIdAndVehicleNoIgnoreCaseAndIdNot(inst, no, self)) err.put("vehicleNo", "This vehicle number already exists.");
        if (reg.isEmpty()) err.put("registrationNo", "Registration number is required.");
        else if (reg.length() > 20) err.put("registrationNo", "At most 20 characters.");
        else if (vehicles.existsByInstitutionIdAndRegistrationNoIgnoreCaseAndIdNot(inst, reg, self)) err.put("registrationNo", "This registration number already exists.");
        Check.oneOf(err, "vehicleType", b.vehicleType(), VEHICLE_TYPES);
        Check.oneOf(err, "fuelType", b.fuelType(), FUEL_TYPES);
        Check.oneOf(err, "status", b.status(), VEHICLE_STATES);
        if (Check.trim(b.make()).isEmpty()) err.put("make", "Make is required.");
        if (Check.trim(b.model()).isEmpty()) err.put("model", "Model is required.");
        Check.max(err, "make", b.make(), 50);
        Check.max(err, "model", b.model(), 50);
        Check.max(err, "remarks", b.remarks(), 300);
        if (b.manufactureYear() != null && (b.manufactureYear() < 1980 || b.manufactureYear() > LocalDate.now().getYear() + 1)) err.put("manufactureYear", "Enter a valid year.");
        if (b.seatingCapacity() == null || b.seatingCapacity() < 1 || b.seatingCapacity() > 200) err.put("seatingCapacity", "Enter 1 to 200 seats.");
        if (b.standingCapacity() != null && (b.standingCapacity() < 0 || b.standingCapacity() > 200)) err.put("standingCapacity", "Enter 0 to 200.");
        var insurance = Check.date(err, "insuranceExpiry", b.insuranceExpiry(), false);
        var puc = Check.date(err, "pucExpiry", b.pucExpiry(), false);
        if (b.routeId() != null && routes.findByIdAndInstitutionId(b.routeId(), inst).isEmpty()) err.put("routeId", "Choose a valid route.");
        if (b.driverId() != null) {
            TransportDriver d = drivers.findByIdAndInstitutionId(b.driverId(), inst).orElse(null);
            if (d == null) err.put("driverId", "Choose a valid driver.");
            else if ("INACTIVE".equals(d.status)) err.put("driverId", "This driver is inactive.");
            else vehicles.findFirstByInstitutionIdAndDriverId(inst, d.getId()).filter(o -> !o.getId().equals(self))
                    .ifPresent(o -> err.put("driverId", d.name + " is already assigned to " + o.vehicleNo + "."));
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        v.vehicleNo = no.toUpperCase();
        v.registrationNo = reg.toUpperCase();
        v.vehicleType = b.vehicleType();
        v.make = b.make().trim();
        v.model = b.model().trim();
        v.manufactureYear = b.manufactureYear();
        v.seatingCapacity = b.seatingCapacity();
        v.standingCapacity = b.standingCapacity();
        v.fuelType = b.fuelType();
        v.insuranceExpiry = insurance;
        v.pucExpiry = puc;
        v.status = b.status();
        v.routeId = b.routeId();
        v.driverId = b.driverId();
        v.remarks = Check.orNull(b.remarks());
    }

    // ------------------------------------------------------------------ drivers

    @Transactional(readOnly = true)
    public List<DriverView> listDrivers() {
        Long inst = current.institutionId();
        Map<Long, String> routeNames = routes.findByInstitutionIdOrderByNameAsc(inst).stream().collect(Collectors.toMap(TransportRoute::getId, r -> r.name));
        Map<Long, TransportVehicle> byDriver = new HashMap<>();
        for (TransportVehicle v : vehicles.findByInstitutionIdOrderByVehicleNoAsc(inst)) if (v.driverId != null) byDriver.put(v.driverId, v);
        return drivers.findByInstitutionIdOrderByNameAsc(inst).stream().map(d -> {
            TransportVehicle v = byDriver.get(d.getId());
            return DriverView.of(d, v == null ? null : v.vehicleNo, v == null || v.routeId == null ? null : routeNames.get(v.routeId));
        }).toList();
    }

    public DriverView createDriver(DriverBody b) {
        Long inst = current.institutionId();
        TransportDriver d = new TransportDriver();
        d.setInstitutionId(inst);
        applyDriver(d, b, inst, 0L);
        d = drivers.saveAndFlush(d);
        audit.record("TRANSPORT_DRIVER_CREATED", "TransportDriver", d.getId(), inst, "Driver " + d.name + " added");
        return driverView(d, inst);
    }

    public DriverView updateDriver(Long id, DriverBody b) {
        Long inst = current.institutionId();
        TransportDriver d = drivers.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Driver"));
        applyDriver(d, b, inst, id);
        d = drivers.saveAndFlush(d);
        audit.record("TRANSPORT_DRIVER_UPDATED", "TransportDriver", id, inst, "Driver " + d.name + " updated");
        return driverView(d, inst);
    }

    /** Vehicles driven by this driver become driver-less. */
    public void deleteDriver(Long id) {
        Long inst = current.institutionId();
        TransportDriver d = drivers.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Driver"));
        vehicles.findFirstByInstitutionIdAndDriverId(inst, id).ifPresent(v -> v.driverId = null);
        vehicles.flush();
        drivers.delete(d);
        audit.record("TRANSPORT_DRIVER_DELETED", "TransportDriver", id, inst, "Driver " + d.name + " deleted");
    }

    private DriverView driverView(TransportDriver d, Long inst) {
        TransportVehicle v = vehicles.findFirstByInstitutionIdAndDriverId(inst, d.getId()).orElse(null);
        String route = v == null || v.routeId == null ? null : routes.findByIdAndInstitutionId(v.routeId, inst).map(r -> r.name).orElse(null);
        return DriverView.of(d, v == null ? null : v.vehicleNo, route);
    }

    private void applyDriver(TransportDriver d, DriverBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String code = Check.trim(b.code());
        if (code.isEmpty()) {
            long n = drivers.countByInstitutionId(inst) + 1;
            do { code = String.format("DRV%03d", n++); } while (drivers.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(inst, code, 0L));
        } else if (code.length() > 20) err.put("code", "At most 20 characters.");
        else if (drivers.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(inst, code, self)) err.put("code", "This driver code already exists.");
        String name = Check.trim(b.name());
        if (name.isEmpty()) err.put("name", "Driver name is required.");
        else if (name.length() > 100) err.put("name", "At most 100 characters.");
        String mobile = Check.trim(b.mobile());
        if (mobile.isEmpty()) err.put("mobile", "Mobile number is required.");
        else if (!mobile.matches("\\+?[0-9][0-9 \\-]{6,17}")) err.put("mobile", "Enter a valid mobile number.");
        String emergencyPhone = Check.trim(b.emergencyPhone());
        if (!emergencyPhone.isEmpty() && !emergencyPhone.matches("\\+?[0-9][0-9 \\-]{6,17}")) err.put("emergencyPhone", "Enter a valid phone number.");
        String license = Check.trim(b.licenseNo());
        if (license.isEmpty()) err.put("licenseNo", "License number is required.");
        else if (license.length() > 40) err.put("licenseNo", "At most 40 characters.");
        else if (drivers.existsByInstitutionIdAndLicenseNoIgnoreCaseAndIdNot(inst, license, self)) err.put("licenseNo", "This license number is already registered.");
        var expiry = Check.date(err, "licenseExpiry", b.licenseExpiry(), true);
        var dob = Check.date(err, "dateOfBirth", b.dateOfBirth(), false);
        if (dob != null && !dob.isBefore(LocalDate.now().minusYears(16))) err.put("dateOfBirth", "Driver must be at least 16 years old.");
        Check.oneOf(err, "licenseType", b.licenseType(), LICENSE_TYPES);
        Check.oneOf(err, "status", b.status(), DRIVER_STATES);
        if (b.gender() != null && !b.gender().isBlank() && !GENDERS.contains(b.gender())) err.put("gender", "Choose one of the listed options.");
        if (b.experienceYears() != null && (b.experienceYears() < 0 || b.experienceYears() > 60)) err.put("experienceYears", "Enter 0 to 60 years.");
        Check.max(err, "address", b.address(), 300);
        Check.max(err, "emergencyName", b.emergencyName(), 100);
        Check.max(err, "remarks", b.remarks(), 300);
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        d.code = code.toUpperCase();
        d.name = name;
        d.mobile = mobile;
        d.dateOfBirth = dob;
        d.gender = Check.orNull(b.gender());
        d.address = Check.orNull(b.address());
        d.licenseNo = license.toUpperCase();
        d.licenseExpiry = expiry;
        d.licenseType = b.licenseType();
        d.experienceYears = b.experienceYears();
        d.emergencyName = Check.orNull(b.emergencyName());
        d.emergencyPhone = Check.orNull(b.emergencyPhone());
        d.status = b.status();
        d.remarks = Check.orNull(b.remarks());
    }
}
