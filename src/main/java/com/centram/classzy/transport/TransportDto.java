package com.centram.classzy.transport;

public final class TransportDto {
    private TransportDto() {}

    static String s(Object o) { return o == null ? null : o.toString(); }

    public record RouteBody(String name, String code, String startLocation, String endLocation, Double distanceKm, Integer durationMinutes, String status, String description) {}

    public record RouteView(Long id, String name, String code, String startLocation, String endLocation, Double distanceKm, Integer durationMinutes, String status,
                            String description, long stopCount, long vehicleCount) {
        static RouteView of(TransportRoute r, long stops, long vehicles) {
            return new RouteView(r.getId(), r.name, r.code, r.startLocation, r.endLocation, r.distanceKm, r.durationMinutes, r.status, r.description, stops, vehicles);
        }
    }

    public record StopBody(Long routeId, String name, String code, Integer sequenceNo, Double latitude, Double longitude, String address, String arrivalTime,
                           String departureTime, Integer radiusMeters, String stopType, String status) {}

    public record StopView(Long id, Long routeId, String name, String code, int sequenceNo, Double latitude, Double longitude, String address, String arrivalTime,
                           String departureTime, int radiusMeters, String stopType, String status) {
        static StopView of(TransportStop x) {
            return new StopView(x.getId(), x.routeId, x.name, x.code, x.sequenceNo, x.latitude, x.longitude, x.address, s(x.arrivalTime), s(x.departureTime), x.radiusMeters, x.stopType, x.status);
        }
    }

    public record VehicleBody(String vehicleNo, String registrationNo, String vehicleType, String make, String model, Integer manufactureYear, Integer seatingCapacity,
                              Integer standingCapacity, String fuelType, String insuranceExpiry, String pucExpiry, String status, Long routeId, Long driverId, String remarks) {}

    public record VehicleView(Long id, String vehicleNo, String registrationNo, String vehicleType, String make, String model, Integer manufactureYear, int seatingCapacity,
                              Integer standingCapacity, String fuelType, String insuranceExpiry, String pucExpiry, String status, Long routeId, Long driverId, String remarks,
                              String routeName, String driverName) {
        static VehicleView of(TransportVehicle v, String routeName, String driverName) {
            return new VehicleView(v.getId(), v.vehicleNo, v.registrationNo, v.vehicleType, v.make, v.model, v.manufactureYear, v.seatingCapacity, v.standingCapacity,
                    v.fuelType, s(v.insuranceExpiry), s(v.pucExpiry), v.status, v.routeId, v.driverId, v.remarks, routeName, driverName);
        }
    }

    public record DriverBody(String code, String name, String mobile, String dateOfBirth, String gender, String address, String licenseNo, String licenseExpiry,
                             String licenseType, Integer experienceYears, String emergencyName, String emergencyPhone, String status, String remarks) {}

    public record DriverView(Long id, String code, String name, String mobile, String dateOfBirth, String gender, String address, String licenseNo, String licenseExpiry,
                             String licenseType, Integer experienceYears, String emergencyName, String emergencyPhone, String status, String remarks,
                             String assignedVehicleNo, String assignedRouteName) {
        static DriverView of(TransportDriver d, String vehicleNo, String routeName) {
            return new DriverView(d.getId(), d.code, d.name, d.mobile, s(d.dateOfBirth), d.gender, d.address, d.licenseNo, s(d.licenseExpiry), d.licenseType,
                    d.experienceYears, d.emergencyName, d.emergencyPhone, d.status, d.remarks, vehicleNo, routeName);
        }
    }
}
