package com.centram.classzy.school.transit;

import java.util.List;
import java.util.Map;

import com.centram.classzy.school.transit.TrnDto.*;
import com.centram.classzy.security.Roles;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school/transit")
@PreAuthorize(Roles.WRITE)
public class TrnController {
    private final TrnService s;
    public TrnController(TrnService s) { this.s = s; }

    @GetMapping("/options") public Options options() { return s.options(); }
    @GetMapping("/overview") public Overview overview() { return s.overview(); }
    @PostMapping("/vehicles") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addVehicle(@RequestBody VehicleBody b) { return s.addVehicle(b); }

    @GetMapping("/routes") public List<RouteRow> routes(@RequestParam(name = "q", required = false) String q) { return s.routes(q); }
    @PostMapping("/routes") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> createRoute(@RequestBody RouteBody b) { return s.createRoute(b); }
    @PutMapping("/routes/{id}") public Map<String, Object> updateRoute(@PathVariable("id") Long id, @RequestBody RouteBody b) { return s.updateRoute(id, b); }
    @PostMapping("/routes/{id}/active") public Map<String, Object> active(@PathVariable("id") Long id, @RequestParam(name = "value") boolean value) { return s.setRouteActive(id, value); }
    @DeleteMapping("/routes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteRoute(@PathVariable("id") Long id) { s.deleteRoute(id); }
    @GetMapping("/routes/{id}/stops") public List<Stop> stops(@PathVariable("id") Long id) { return s.stops(id); }
    @PutMapping("/routes/{id}/stops") public List<Stop> saveStops(@PathVariable("id") Long id, @RequestBody StopsBody b) { return s.saveStops(id, b); }

    @GetMapping("/students") public List<StudentOpt> students(@RequestParam(name = "sectionId") Long sectionId) { return s.students(sectionId); }
    @GetMapping("/allocations") public AllocPage allocations(@RequestParam(name = "classId", required = false) Long classId, @RequestParam(name = "routeId", required = false) Long routeId,
                                                              @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "10") int size) { return s.allocations(classId, routeId, page, size); }
    @PostMapping("/allocations") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> allocate(@RequestBody AllocBody b) { return s.allocate(b); }
    @PutMapping("/allocations/{id}") public Map<String, Object> reallocate(@PathVariable("id") Long id, @RequestBody AllocBody b) { return s.reallocate(id, b); }
    @DeleteMapping("/allocations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deallocate(@PathVariable("id") Long id) { s.deallocate(id); }

    @GetMapping("/live/{routeId}") public Live live(@PathVariable("routeId") Long routeId) { return s.live(routeId); }
    @PutMapping("/live/{routeId}") public Live updateTrip(@PathVariable("routeId") Long routeId, @RequestBody TripBody b) { return s.updateTrip(routeId, b); }

    @GetMapping("/staff") public List<StaffRow> staff(@RequestParam(name = "role", required = false) String role) { return s.staff(role); }
    @PostMapping("/staff") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> addStaff(@RequestBody StaffBody b) { return s.addStaff(b); }
    @PutMapping("/staff/{kind}/{id}") public Map<String, Object> updateStaff(@PathVariable("kind") String kind, @PathVariable("id") Long id, @RequestBody StaffBody b) { return s.updateStaff(kind, id, b); }
    @DeleteMapping("/staff/{kind}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteStaff(@PathVariable("kind") String kind, @PathVariable("id") Long id) { s.deleteStaff(kind, id); }

    @GetMapping("/reports/{type}") public Table report(@PathVariable("type") String type, @RequestParam(name = "from", required = false) String from, @RequestParam(name = "to", required = false) String to, @RequestParam(name = "routeId", required = false) Long routeId) { return s.report(type, from, to, routeId); }
}
