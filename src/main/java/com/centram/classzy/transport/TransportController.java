package com.centram.classzy.transport;

import java.util.List;

import com.centram.classzy.security.Roles;
import com.centram.classzy.transport.TransportDto.DriverBody;
import com.centram.classzy.transport.TransportDto.DriverView;
import com.centram.classzy.transport.TransportDto.RouteBody;
import com.centram.classzy.transport.TransportDto.RouteView;
import com.centram.classzy.transport.TransportDto.StopBody;
import com.centram.classzy.transport.TransportDto.StopView;
import com.centram.classzy.transport.TransportDto.VehicleBody;
import com.centram.classzy.transport.TransportDto.VehicleView;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport")
@PreAuthorize(Roles.READ)
public class TransportController {

    private final TransportService transport;
    private final FleetService fleet;

    public TransportController(TransportService transport, FleetService fleet) {
        this.transport = transport;
        this.fleet = fleet;
    }

    // ---- routes
    @GetMapping("/routes")
    public List<RouteView> routes() { return transport.listRoutes(); }

    @PostMapping("/routes")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public RouteView createRoute(@RequestBody RouteBody b) { return transport.createRoute(b); }

    @PutMapping("/routes/{id}")
    @PreAuthorize(Roles.WRITE)
    public RouteView updateRoute(@PathVariable("id") Long id, @RequestBody RouteBody b) { return transport.updateRoute(id, b); }

    @DeleteMapping("/routes/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoute(@PathVariable("id") Long id) { transport.deleteRoute(id); }

    // ---- stops
    @GetMapping("/stops")
    public List<StopView> stops(@RequestParam(name = "routeId") Long routeId) { return transport.listStops(routeId); }

    @PostMapping("/stops")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public StopView createStop(@RequestBody StopBody b) { return transport.createStop(b); }

    @PutMapping("/stops/{id}")
    @PreAuthorize(Roles.WRITE)
    public StopView updateStop(@PathVariable("id") Long id, @RequestBody StopBody b) { return transport.updateStop(id, b); }

    @DeleteMapping("/stops/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStop(@PathVariable("id") Long id) { transport.deleteStop(id); }

    // ---- vehicles
    @GetMapping("/vehicles")
    public List<VehicleView> vehicles() { return fleet.listVehicles(); }

    @PostMapping("/vehicles")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleView createVehicle(@RequestBody VehicleBody b) { return fleet.createVehicle(b); }

    @PutMapping("/vehicles/{id}")
    @PreAuthorize(Roles.WRITE)
    public VehicleView updateVehicle(@PathVariable("id") Long id, @RequestBody VehicleBody b) { return fleet.updateVehicle(id, b); }

    @DeleteMapping("/vehicles/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVehicle(@PathVariable("id") Long id) { fleet.deleteVehicle(id); }

    // ---- drivers
    @GetMapping("/drivers")
    public List<DriverView> drivers() { return fleet.listDrivers(); }

    @PostMapping("/drivers")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public DriverView createDriver(@RequestBody DriverBody b) { return fleet.createDriver(b); }

    @PutMapping("/drivers/{id}")
    @PreAuthorize(Roles.WRITE)
    public DriverView updateDriver(@PathVariable("id") Long id, @RequestBody DriverBody b) { return fleet.updateDriver(id, b); }

    @DeleteMapping("/drivers/{id}")
    @PreAuthorize(Roles.WRITE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDriver(@PathVariable("id") Long id) { fleet.deleteDriver(id); }
}
