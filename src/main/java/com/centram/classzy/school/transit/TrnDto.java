package com.centram.classzy.school.transit;

import java.util.List;

public final class TrnDto {
    private TrnDto() {}
    public record Code(String code, String label) {}
    public record RouteOpt(Long id, String name, String code, String tripType, String status, Long vehicleId) {}
    public record VehicleOpt(Long id, String number, String type, int capacity, String status, String driver) {}
    public record ClassOpt(Long id, String name) {}
    public record SectionOpt(Long id, Long classId, String className, String name) {}
    public record Options(String today, List<RouteOpt> routes, List<VehicleOpt> vehicles, List<ClassOpt> classes, List<SectionOpt> sections, List<Code> tripTypes, List<Code> vehicleTypes, List<Code> tripStatuses) {}

    public record TripLine(Long routeId, String route, String tripType, String vehicle, String status, int delayMinutes) {}
    public record Overview(long routes, long vehicles, long students, List<TripLine> trips) {}

    public record RouteRow(Long id, String name, String code, String tripType, Long vehicleId, String vehicle, int stops, long students, String status) {}
    public record RouteBody(String name, String code, Long vehicleId, String tripType, String stops) {}
    public record Stop(Long id, String name, int sequence, String arrival, long students) {}
    public record StopIn(Long id, String name, String arrival) {}
    public record StopsBody(List<StopIn> stops) {}
    public record VehicleBody(String vehicleNo, String registrationNo, String type, String make, String model, Integer seatingCapacity) {}

    public record StudentOpt(Long id, String admissionNo, String name, String pickupRoute, String dropRoute) {}
    public record Allocation(Long id, Long studentId, String student, String admissionNo, String grade, Long routeId, String route, String tripType, Long stopId, String stop) {}
    public record AllocPage(List<Allocation> items, long total) {}
    public record AllocBody(Long studentId, Long routeId, Long stopId) {}

    public record StopLive(Long id, String name, int sequence, String arrival, boolean reached, boolean current) {}
    public record Live(Long routeId, String route, String tripType, String vehicle, boolean hasTrip, String status, int delayMinutes, Long currentStopId, String updatedAt, String eta, List<StopLive> stops) {}
    public record TripBody(String status, Long currentStopId, Integer delayMinutes) {}

    public record StaffRow(String key, String kind, Long id, String name, String role, String phone, String licenseNo, String licenseExpiry, Long vehicleId, String vehicle, String status) {}
    public record StaffBody(String role, String name, String phone, String licenseNo, String licenseExpiry, Long vehicleId, String status) {}

    public record Table(String title, String subtitle, List<String> columns, List<Boolean> numeric, List<List<String>> rows, List<String> total, String note) {}
}
