package com.centram.classzy.transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.transport.TransportDto.RouteBody;
import com.centram.classzy.transport.TransportDto.RouteView;
import com.centram.classzy.transport.TransportDto.StopBody;
import com.centram.classzy.transport.TransportDto.StopView;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transport routes and the ordered stops on them. */
@Service
@Transactional
public class TransportService {

    private static final Set<String> STOP_TYPES = Set.of("PICKUP_DROP", "PICKUP", "DROP");

    private final CurrentUser current;
    private final TransportRouteRepository routes;
    private final TransportStopRepository stops;
    private final TransportVehicleRepository vehicles;
    private final AuditService audit;

    public TransportService(CurrentUser current, TransportRouteRepository routes, TransportStopRepository stops, TransportVehicleRepository vehicles, AuditService audit) {
        this.current = current;
        this.routes = routes;
        this.stops = stops;
        this.vehicles = vehicles;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ routes

    @Transactional(readOnly = true)
    public List<RouteView> listRoutes() {
        Long inst = current.institutionId();
        Map<Long, Long> stopCounts = counts(stops.countByRoute(inst));
        Map<Long, Long> vehicleCounts = counts(vehicles.countByRoute(inst));
        return routes.findByInstitutionIdOrderByNameAsc(inst).stream()
                .map(r -> RouteView.of(r, stopCounts.getOrDefault(r.getId(), 0L), vehicleCounts.getOrDefault(r.getId(), 0L))).toList();
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put((Long) r[0], (Long) r[1]);
        return m;
    }

    public RouteView createRoute(RouteBody b) {
        Long inst = current.institutionId();
        TransportRoute r = new TransportRoute();
        r.setInstitutionId(inst);
        applyRoute(r, b, inst, 0L);
        r = routes.saveAndFlush(r);
        audit.record("TRANSPORT_ROUTE_CREATED", "TransportRoute", r.getId(), inst, "Route " + r.name + " created");
        return RouteView.of(r, 0, 0);
    }

    public RouteView updateRoute(Long id, RouteBody b) {
        Long inst = current.institutionId();
        TransportRoute r = requireRoute(id, inst);
        applyRoute(r, b, inst, id);
        r = routes.saveAndFlush(r);
        audit.record("TRANSPORT_ROUTE_UPDATED", "TransportRoute", id, inst, "Route " + r.name + " updated");
        return RouteView.of(r, counts(stops.countByRoute(inst)).getOrDefault(id, 0L), counts(vehicles.countByRoute(inst)).getOrDefault(id, 0L));
    }

    /** Deleting a route also deletes its stops; vehicles on it simply become unassigned. */
    public void deleteRoute(Long id) {
        Long inst = current.institutionId();
        TransportRoute r = requireRoute(id, inst);
        routes.delete(r);
        audit.record("TRANSPORT_ROUTE_DELETED", "TransportRoute", id, inst, "Route " + r.name + " deleted");
    }

    private TransportRoute requireRoute(Long id, Long inst) {
        if (id == null) throw ApiException.badRequest("Choose a route.");
        return routes.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Route"));
    }

    private void applyRoute(TransportRoute r, RouteBody b, Long inst, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = Check.trim(b.name());
        String code = Check.trim(b.code());
        if (name.isEmpty()) err.put("name", "Route name is required.");
        else if (name.length() > 100) err.put("name", "At most 100 characters.");
        else if (routes.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(inst, name, self)) err.put("name", "A route with this name already exists.");
        if (code.isEmpty()) err.put("code", "Route code is required.");
        else if (code.length() > 20) err.put("code", "At most 20 characters.");
        else if (routes.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(inst, code, self)) err.put("code", "A route with this code already exists.");
        if (Check.trim(b.startLocation()).isEmpty()) err.put("startLocation", "Start location is required.");
        if (Check.trim(b.endLocation()).isEmpty()) err.put("endLocation", "End location is required.");
        Check.max(err, "startLocation", b.startLocation(), 150);
        Check.max(err, "endLocation", b.endLocation(), 150);
        if (b.distanceKm() != null && (b.distanceKm() < 0 || b.distanceKm() > 2000)) err.put("distanceKm", "Enter 0 to 2000 km.");
        if (b.durationMinutes() != null && (b.durationMinutes() < 1 || b.durationMinutes() > 600)) err.put("durationMinutes", "Enter 1 to 600 minutes.");
        Check.oneOf(err, "status", b.status(), Check.ACTIVE_STATES);
        Check.max(err, "description", b.description(), 500);
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        r.name = name;
        r.code = code.toUpperCase();
        r.startLocation = b.startLocation().trim();
        r.endLocation = b.endLocation().trim();
        r.distanceKm = b.distanceKm();
        r.durationMinutes = b.durationMinutes();
        r.status = b.status();
        r.description = Check.orNull(b.description());
    }

    // ------------------------------------------------------------------ stops

    @Transactional(readOnly = true)
    public List<StopView> listStops(Long routeId) {
        Long inst = current.institutionId();
        requireRoute(routeId, inst);
        return stops.findByInstitutionIdAndRouteIdOrderBySequenceNoAscIdAsc(inst, routeId).stream().map(StopView::of).toList();
    }

    public StopView createStop(StopBody b) {
        Long inst = current.institutionId();
        TransportRoute route = requireRoute(b.routeId(), inst);
        TransportStop s = new TransportStop();
        s.setInstitutionId(inst);
        s.routeId = route.getId();
        s.sequenceNo = 1_000_000;                         // parked at the end until placed below
        applyStop(s, b, 0L);
        s = stops.saveAndFlush(s);
        place(s, b.sequenceNo(), inst);
        audit.record("TRANSPORT_STOP_CREATED", "TransportStop", s.getId(), inst, "Stop " + s.name + " added to " + route.name);
        return StopView.of(s);
    }

    public StopView updateStop(Long id, StopBody b) {
        Long inst = current.institutionId();
        TransportStop s = stops.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Stop"));
        if (b.routeId() != null && !b.routeId().equals(s.routeId)) throw ApiException.badRequest("A stop cannot be moved to another route.");
        applyStop(s, b, id);
        s = stops.saveAndFlush(s);
        place(s, b.sequenceNo() != null ? b.sequenceNo() : s.sequenceNo, inst);
        audit.record("TRANSPORT_STOP_UPDATED", "TransportStop", id, inst, "Stop " + s.name + " updated");
        return StopView.of(s);
    }

    public void deleteStop(Long id) {
        Long inst = current.institutionId();
        TransportStop s = stops.findByIdAndInstitutionId(id, inst).orElseThrow(() -> ApiException.notFound("Stop"));
        Long routeId = s.routeId;
        stops.delete(s);
        stops.flush();
        int n = 1;
        for (TransportStop x : stops.findByInstitutionIdAndRouteIdOrderBySequenceNoAscIdAsc(inst, routeId)) x.sequenceNo = n++;
        audit.record("TRANSPORT_STOP_DELETED", "TransportStop", id, inst, "Stop " + s.name + " deleted");
    }

    /** Puts the stop at the wanted position (1 = first) and renumbers the route 1..n. */
    private void place(TransportStop s, Integer wanted, Long inst) {
        List<TransportStop> list = new ArrayList<>(stops.findByInstitutionIdAndRouteIdOrderBySequenceNoAscIdAsc(inst, s.routeId));
        list.removeIf(x -> x.getId().equals(s.getId()));
        int index = wanted == null ? list.size() : Math.max(0, Math.min(wanted - 1, list.size()));
        list.add(index, s);
        for (int i = 0; i < list.size(); i++) list.get(i).sequenceNo = i + 1;
    }

    private void applyStop(TransportStop s, StopBody b, long self) {
        Map<String, String> err = new LinkedHashMap<>();
        String name = Check.trim(b.name());
        if (name.isEmpty()) err.put("name", "Stop name is required.");
        else if (name.length() > 100) err.put("name", "At most 100 characters.");
        else if (stops.existsByRouteIdAndNameIgnoreCaseAndIdNot(s.routeId, name, self)) err.put("name", "This route already has a stop with this name.");
        Check.max(err, "code", b.code(), 20);
        Check.max(err, "address", b.address(), 300);
        if (b.sequenceNo() != null && b.sequenceNo() < 1) err.put("sequenceNo", "Must be 1 or more.");
        if ((b.latitude() == null) != (b.longitude() == null)) err.put(b.latitude() == null ? "latitude" : "longitude", "Enter both latitude and longitude, or neither.");
        if (b.latitude() != null && (b.latitude() < -90 || b.latitude() > 90)) err.put("latitude", "Latitude must be between -90 and 90.");
        if (b.longitude() != null && (b.longitude() < -180 || b.longitude() > 180)) err.put("longitude", "Longitude must be between -180 and 180.");
        var arrival = Check.time(err, "arrivalTime", b.arrivalTime());
        var departure = Check.time(err, "departureTime", b.departureTime());
        if (arrival != null && departure != null && departure.isBefore(arrival)) err.put("departureTime", "Departure can't be before arrival.");
        int radius = b.radiusMeters() == null ? 100 : b.radiusMeters();
        if (radius < 10 || radius > 1000) err.put("radiusMeters", "Enter 10 to 1000 metres.");
        Check.oneOf(err, "stopType", b.stopType(), STOP_TYPES);
        Check.oneOf(err, "status", b.status(), Check.ACTIVE_STATES);
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        s.name = name;
        s.code = Check.orNull(b.code());
        s.latitude = b.latitude();
        s.longitude = b.longitude();
        s.address = Check.orNull(b.address());
        s.arrivalTime = arrival;
        s.departureTime = departure;
        s.radiusMeters = radius;
        s.stopType = b.stopType();
        s.status = b.status();
    }
}
