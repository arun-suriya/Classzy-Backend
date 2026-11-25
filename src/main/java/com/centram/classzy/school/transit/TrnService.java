package com.centram.classzy.school.transit;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.school.transit.TrnDto.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transport desk: routes and stops, student allocation, the manually updated trip board, transport staff and reports. */
@Service
@Transactional
public class TrnService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final List<Code> TRIP_TYPES = List.of(new Code("PICKUP", "Pickup"), new Code("DROP", "Drop-off"));
    static final List<Code> VEHICLE_TYPES = List.of(new Code("BUS", "Bus"), new Code("VAN", "Van"), new Code("CAR", "Car"), new Code("OTHER", "Other"));
    static final List<Code> TRIP_STATUSES = List.of(new Code("SCHEDULED", "Scheduled"), new Code("ON_TIME", "On Time"), new Code("DELAYED", "Delayed"), new Code("COMPLETED", "Completed"), new Code("CANCELLED", "Cancelled"));
    static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final AuditService audit;

    public TrnService(JdbcTemplate jdbc, CurrentUser current, AuditService audit) { this.jdbc = jdbc; this.current = current; this.audit = audit; }

    private long inst() { return current.institutionId(); }
    private String actor() { return current.principal().email(); }
    private long count(String sql, Object... a) { Long n = jdbc.queryForObject(sql, Long.class, a); return n == null ? 0 : n; }
    private static LocalDate today() { return LocalDate.now(IST); }
    private static void fail(Map<String, String> e) { if (!e.isEmpty()) throw new FieldErrorsException(e); }
    private static String clip(String v, int max) { return v == null || v.trim().isEmpty() ? null : (v.trim().length() > max ? v.trim().substring(0, max) : v.trim()); }
    private static boolean has(List<Code> l, String c) { return c != null && l.stream().anyMatch((x) -> x.code().equals(c)); }
    private static String hm(Time t) { return t == null ? null : t.toString().substring(0, 5); }
    private static double pct(double a, double b) { return b <= 0 ? 0 : Math.round(a * 1000.0 / b) / 10.0; }
    private static void maxLen(Map<String, String> err, String key, String v, int max, String what) { if (v != null && v.trim().length() > max) err.put(key, what + " can be at most " + max + " characters."); }
    private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^[0-9+()\\-\\s]{6,20}$");

    private static LocalDate date(String v, String key, Map<String, String> err, boolean required) {
        if (v == null || v.isBlank()) { if (required) err.put(key, "Choose a date."); return null; }
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { err.put(key, "That isn't a valid date."); return null; }
    }
    private static Time time(String v, String key, Map<String, String> err) {
        if (v == null || v.isBlank()) return null;
        try { return Time.valueOf(LocalTime.parse(v.trim())); } catch (DateTimeParseException e) { err.put(key, "Enter a valid time."); return null; }
    }

    // ------------------------------------------------------------------ options & overview

    @Transactional(readOnly = true)
    public Options options() {
        long i = inst();
        List<Long> yr = jdbc.queryForList("select id from academic_years where institution_id = ? and active order by is_current desc, start_date desc limit 1", Long.class, i);
        Long year = yr.isEmpty() ? null : yr.get(0);
        return new Options(today().toString(),
            jdbc.query("select id, name, code, trip_type, status, vehicle_id from transport_routes where institution_id = ? order by name", (rs, n) -> new RouteOpt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), (Long) rs.getObject(6)), i),
            jdbc.query("select v.id, v.vehicle_no, v.vehicle_type, v.seating_capacity, v.status, d.name from transport_vehicles v left join transport_drivers d on d.id = v.driver_id where v.institution_id = ? order by v.vehicle_no", (rs, n) -> new VehicleOpt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6)), i),
            year == null ? List.of() : jdbc.query("select id, name from school_classes where institution_id = ? and academic_year_id = ? and active order by coalesce(grade_level, -1), name", (rs, n) -> new ClassOpt(rs.getLong(1), rs.getString(2)), i, year),
            year == null ? List.of() : jdbc.query("select s.id, c.id, c.name, s.name from sections s join school_classes c on c.id = s.school_class_id where s.institution_id = ? and c.academic_year_id = ? and s.active and c.active order by coalesce(c.grade_level, -1), c.name, s.name", (rs, n) -> new SectionOpt(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)), i, year),
            TRIP_TYPES, VEHICLE_TYPES, TRIP_STATUSES);
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        long i = inst();
        List<TripLine> trips = jdbc.query("select r.id, r.name, r.trip_type, v.vehicle_no, coalesce(t.status, 'SCHEDULED'), coalesce(t.delay_minutes, 0) from transport_routes r left join transport_vehicles v on v.id = r.vehicle_id left join school_trn_trips t on t.route_id = r.id and t.trip_date = ? where r.institution_id = ? and r.status = 'ACTIVE' order by r.name",
                (rs, n) -> new TripLine(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6)), Date.valueOf(today()), i);
        return new Overview(count("select count(*) from transport_routes where institution_id = ? and status = 'ACTIVE'", i), count("select count(*) from transport_vehicles where institution_id = ? and status = 'ACTIVE'", i),
                count("select count(distinct student_id) from school_trn_allocations where institution_id = ?", i), trips);
    }

    // ------------------------------------------------------------------ vehicles

    public Map<String, Object> addVehicle(VehicleBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        String no = clip(b.vehicleNo(), 20), reg = clip(b.registrationNo(), 20), make = clip(b.make(), 50), model = clip(b.model(), 50);
        if (no == null) err.put("vehicleNo", "Enter the vehicle number."); else if (count("select count(*) from transport_vehicles where institution_id = ? and lower(vehicle_no) = ?", inst(), no.toLowerCase(Locale.ROOT)) > 0) err.put("vehicleNo", "This vehicle number already exists.");
        if (reg == null) err.put("registrationNo", "Enter the registration number."); else if (count("select count(*) from transport_vehicles where institution_id = ? and lower(registration_no) = ?", inst(), reg.toLowerCase(Locale.ROOT)) > 0) err.put("registrationNo", "This registration number already exists.");
        maxLen(err, "vehicleNo", b.vehicleNo(), 20, "The vehicle number"); maxLen(err, "registrationNo", b.registrationNo(), 20, "The registration number");
        if (make == null) err.put("make", "Enter the make."); if (model == null) err.put("model", "Enter the model.");
        String type = b.type() == null || b.type().isBlank() ? "BUS" : b.type().trim();
        if (!has(VEHICLE_TYPES, type)) err.put("type", "Choose a vehicle type.");
        if (b.seatingCapacity() == null || b.seatingCapacity() < 1 || b.seatingCapacity() > 100) err.put("seatingCapacity", "Seating capacity must be from 1 to 100.");
        fail(err);
        Long id = jdbc.queryForObject("insert into transport_vehicles (institution_id, vehicle_no, registration_no, vehicle_type, make, model, seating_capacity) values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class, inst(), no, reg, type, make, model, b.seatingCapacity());
        audit.record("VEHICLE_ADDED", "VEHICLE", id, inst(), "Vehicle added - " + no);
        return Map.of("id", id);
    }

    // ------------------------------------------------------------------ routes & stops

    @Transactional(readOnly = true)
    public List<RouteRow> routes(String q) {
        StringBuilder sql = new StringBuilder("select r.id, r.name, r.code, r.trip_type, r.vehicle_id, v.vehicle_no, (select count(*) from transport_stops s where s.route_id = r.id), (select count(*) from school_trn_allocations a where a.route_id = r.id), r.status from transport_routes r left join transport_vehicles v on v.id = r.vehicle_id where r.institution_id = ?");
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (q != null && !q.isBlank()) { sql.append(" and (lower(r.name) like ? or lower(r.code) like ?)"); String k = "%" + q.trim().toLowerCase(Locale.ROOT) + "%"; a.add(k); a.add(k); }
        sql.append(" order by r.name");
        return jdbc.query(sql.toString(), (rs, n) -> new RouteRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), (Long) rs.getObject(5), rs.getString(6), rs.getInt(7), rs.getLong(8), rs.getString(9)), a.toArray());
    }

    private void checkRoute(RouteBody b, Long selfId, Map<String, String> err) {
        String name = clip(b.name(), 100), code = clip(b.code(), 20);
        if (name == null) err.put("name", "Enter the route name."); else if (count("select count(*) from transport_routes where institution_id = ? and lower(name) = ? and id <> ?", inst(), name.toLowerCase(Locale.ROOT), selfId == null ? -1L : selfId) > 0) err.put("name", "A route with this name already exists.");
        if (code == null) err.put("code", "Enter the route code."); else if (count("select count(*) from transport_routes where institution_id = ? and lower(code) = ? and id <> ?", inst(), code.toLowerCase(Locale.ROOT), selfId == null ? -1L : selfId) > 0) err.put("code", "A route with this code already exists.");
        maxLen(err, "name", b.name(), 100, "The name"); maxLen(err, "code", b.code(), 20, "The code");
        if (!has(TRIP_TYPES, b.tripType())) err.put("tripType", "Choose Pickup or Drop-off.");
        if (b.vehicleId() == null) err.put("vehicleId", "Choose a vehicle.");
        else {
            List<String> st = jdbc.queryForList("select status from transport_vehicles where id = ? and institution_id = ?", String.class, b.vehicleId(), inst());
            if (st.isEmpty()) err.put("vehicleId", "Choose a vehicle from this school.");
            else if (!"ACTIVE".equals(st.get(0))) err.put("vehicleId", "This vehicle isn't active.");
        }
    }

    private static List<String> parseStops(String s, Map<String, String> err) {
        List<String> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        if (s != null) for (String p : s.split("[,\\n]")) {
            String n = p.trim();
            if (n.isEmpty()) continue;
            if (n.length() > 100) { err.put("stops", "A stop name can be at most 100 characters."); return out; }
            if (!seen.add(n.toLowerCase(Locale.ROOT))) { err.put("stops", "\"" + n + "\" is listed twice."); return out; }
            out.add(n);
        }
        if (out.size() < 2) err.putIfAbsent("stops", "Add at least two stops, for example the first pickup and the school.");
        if (out.size() > 40) err.put("stops", "A route can have at most 40 stops.");
        return out;
    }

    public Map<String, Object> createRoute(RouteBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        checkRoute(b, null, err);
        List<String> stops = parseStops(b.stops(), err);
        fail(err);
        Long id = jdbc.queryForObject("insert into transport_routes (institution_id, name, code, start_location, end_location, status, trip_type, vehicle_id) values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?) returning id", Long.class,
                inst(), b.name().trim(), b.code().trim(), clip(stops.get(0), 150), clip(stops.get(stops.size() - 1), 150), b.tripType(), b.vehicleId());
        for (int k = 0; k < stops.size(); k++) jdbc.update("insert into transport_stops (institution_id, route_id, name, sequence_no) values (?, ?, ?, ?)", inst(), id, stops.get(k), k + 1);
        audit.record("ROUTE_ADDED", "ROUTE", id, inst(), "Route added - " + b.name().trim());
        return Map.of("id", id);
    }

    public Map<String, Object> updateRoute(Long id, RouteBody b) {
        List<String> cur = jdbc.queryForList("select trip_type from transport_routes where id = ? and institution_id = ?", String.class, id, inst());
        if (cur.isEmpty()) throw ApiException.notFound("That route wasn't found.");
        Map<String, String> err = new LinkedHashMap<>();
        checkRoute(b, id, err);
        if (!cur.get(0).equals(b.tripType()) && count("select count(*) from school_trn_allocations where route_id = ?", id) > 0) err.put("tripType", "Students are allocated to this route, so its trip type can't change.");
        fail(err);
        jdbc.update("update transport_routes set name = ?, code = ?, trip_type = ?, vehicle_id = ? where id = ?", b.name().trim(), b.code().trim(), b.tripType(), b.vehicleId(), id);
        audit.record("ROUTE_UPDATED", "ROUTE", id, inst(), "Route updated - " + b.name().trim());
        return Map.of("id", id);
    }

    public Map<String, Object> setRouteActive(Long id, boolean active) {
        if (jdbc.update("update transport_routes set status = ? where id = ? and institution_id = ?", active ? "ACTIVE" : "INACTIVE", id, inst()) == 0) throw ApiException.notFound("That route wasn't found.");
        audit.record(active ? "ROUTE_ACTIVATED" : "ROUTE_DEACTIVATED", "ROUTE", id, inst(), active ? "Route activated" : "Route deactivated");
        return Map.of("id", id, "active", active);
    }

    public void deleteRoute(Long id) {
        List<String> n = jdbc.queryForList("select name from transport_routes where id = ? and institution_id = ?", String.class, id, inst());
        if (n.isEmpty()) throw ApiException.notFound("That route wasn't found.");
        long al = count("select count(*) from school_trn_allocations where route_id = ?", id);
        if (al > 0) throw ApiException.conflict(n.get(0) + " has " + al + " student" + (al == 1 ? "" : "s") + " allocated. Move them to another route first.");
        jdbc.update("delete from transport_routes where id = ?", id);
        audit.record("ROUTE_DELETED", "ROUTE", id, inst(), "Route deleted - " + n.get(0));
    }

    private void ownRoute(Long id) { if (id == null || count("select count(*) from transport_routes where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That route wasn't found."); }

    @Transactional(readOnly = true)
    public List<Stop> stops(Long routeId) {
        ownRoute(routeId);
        return jdbc.query("select s.id, s.name, s.sequence_no, s.arrival_time, (select count(*) from school_trn_allocations a where a.stop_id = s.id) from transport_stops s where s.route_id = ? order by s.sequence_no, s.id", (rs, n) -> new Stop(rs.getLong(1), rs.getString(2), rs.getInt(3), hm(rs.getTime(4)), rs.getLong(5)), routeId);
    }

    public List<Stop> saveStops(Long routeId, StopsBody b) {
        ownRoute(routeId);
        List<StopIn> in = b.stops() == null ? List.of() : b.stops();
        Map<String, String> err = new LinkedHashMap<>();
        if (in.size() < 2) err.put("stops", "A route needs at least two stops.");
        if (in.size() > 40) err.put("stops", "A route can have at most 40 stops.");
        Set<String> seen = new HashSet<>(); Set<Long> keep = new HashSet<>(); List<Time> times = new ArrayList<>();
        Set<Long> mine = new HashSet<>(jdbc.queryForList("select id from transport_stops where route_id = ?", Long.class, routeId));
        for (int k = 0; k < in.size(); k++) {
            StopIn s = in.get(k);
            String n = clip(s.name(), 100);
            if (n == null) err.put("name" + k, "Enter the stop name.");
            else if (s.name().trim().length() > 100) err.put("name" + k, "At most 100 characters.");
            else if (!seen.add(n.toLowerCase(Locale.ROOT))) err.put("name" + k, "Two stops have this name.");
            if (s.id() != null) { if (!mine.contains(s.id())) err.put("name" + k, "This stop isn't on this route."); else keep.add(s.id()); }
            times.add(time(s.arrival(), "time" + k, err));
        }
        for (Long old : mine) if (!keep.contains(old)) {
            long al = count("select count(*) from school_trn_allocations where stop_id = ?", old);
            if (al > 0) err.put("stops", "\"" + jdbc.queryForObject("select name from transport_stops where id = ?", String.class, old) + "\" has " + al + " student" + (al == 1 ? "" : "s") + " allocated. Move them before removing the stop.");
        }
        fail(err);
        for (Long old : mine) if (!keep.contains(old)) jdbc.update("delete from transport_stops where id = ?", old);
        for (Long k : keep) jdbc.update("update transport_stops set name = '~' || id where id = ?", k);   // avoids clashes while names swap
        for (int k = 0; k < in.size(); k++) {
            StopIn s = in.get(k);
            if (s.id() == null) jdbc.update("insert into transport_stops (institution_id, route_id, name, sequence_no, arrival_time) values (?, ?, ?, ?, ?)", inst(), routeId, s.name().trim(), k + 1, times.get(k));
            else jdbc.update("update transport_stops set name = ?, sequence_no = ?, arrival_time = ? where id = ?", s.name().trim(), k + 1, times.get(k), s.id());
        }
        jdbc.update("update transport_routes set start_location = ?, end_location = ? where id = ?", clip(in.get(0).name(), 150), clip(in.get(in.size() - 1).name(), 150), routeId);
        audit.record("ROUTE_STOPS_SAVED", "ROUTE", routeId, inst(), "Route stops saved (" + in.size() + ")");
        return stops(routeId);
    }

    // ------------------------------------------------------------------ student allocation

    @Transactional(readOnly = true)
    public List<StudentOpt> students(Long sectionId) {
        if (sectionId == null) throw ApiException.badRequest("Choose a section.");
        return jdbc.query("select s.id, s.admission_no, s.full_name, "
                + "(select r.name from school_trn_allocations a join transport_routes r on r.id = a.route_id where a.student_id = s.id and a.trip_type = 'PICKUP'), "
                + "(select r.name from school_trn_allocations a join transport_routes r on r.id = a.route_id where a.student_id = s.id and a.trip_type = 'DROP') "
                + "from students s where s.institution_id = ? and s.section_id = ? and s.active order by s.full_name", (rs, n) -> new StudentOpt(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), inst(), sectionId);
    }

    @Transactional(readOnly = true)
    public AllocPage allocations(Long classId, Long routeId, int page, int size) {
        StringBuilder w = new StringBuilder(" where a.institution_id = ?");
        List<Object> a = new ArrayList<>(List.of(inst()));
        if (classId != null) { w.append(" and s.school_class_id = ?"); a.add(classId); }
        if (routeId != null) { w.append(" and a.route_id = ?"); a.add(routeId); }
        String from = " from school_trn_allocations a join students s on s.id = a.student_id join transport_routes r on r.id = a.route_id join transport_stops st on st.id = a.stop_id left join school_classes c on c.id = s.school_class_id left join sections sc on sc.id = s.section_id";
        long total = count("select count(*)" + from + w, a.toArray());
        int sz = Math.max(1, Math.min(size, 100));
        List<Object> b = new ArrayList<>(a); b.add(sz); b.add(Math.max(0, page) * sz);
        List<Allocation> rows = jdbc.query("select a.id, s.id, s.full_name, s.admission_no, coalesce(c.name || ' – ' || sc.name, ''), r.id, r.name, a.trip_type, st.id, st.name" + from + w + " order by s.full_name, a.trip_type limit ? offset ?",
                (rs, n) -> new Allocation(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getLong(9), rs.getString(10)), b.toArray());
        return new AllocPage(rows, total);
    }

    private record Rt(String name, String type, String status, Long vehicle) {}
    private Rt checkAlloc(AllocBody b, Long allocId, Map<String, String> err) {
        Rt rt = null;
        if (b.routeId() == null) err.put("routeId", "Choose a route.");
        else {
            List<Rt> l = jdbc.query("select name, trip_type, status, vehicle_id from transport_routes where id = ? and institution_id = ?", (rs, n) -> new Rt(rs.getString(1), rs.getString(2), rs.getString(3), (Long) rs.getObject(4)), b.routeId(), inst());
            if (l.isEmpty()) err.put("routeId", "Choose a route from this school.");
            else {
                rt = l.get(0);
                if (!"ACTIVE".equals(rt.status())) err.put("routeId", "This route isn't active.");
                else if (rt.vehicle() == null) err.put("routeId", "Assign a vehicle to this route first.");
            }
        }
        if (b.stopId() == null) err.put("stopId", "Choose a pickup/drop stop.");
        else if (b.routeId() != null && count("select count(*) from transport_stops where id = ? and route_id = ?", b.stopId(), b.routeId()) == 0) err.put("stopId", "This stop isn't on the chosen route.");
        if (rt != null && err.isEmpty()) {
            Integer cap = jdbc.queryForObject("select seating_capacity from transport_vehicles where id = ?", Integer.class, rt.vehicle());
            long used = count("select count(*) from school_trn_allocations where route_id = ? and id <> ?", b.routeId(), allocId == null ? -1L : allocId);
            if (cap != null && used >= cap) err.put("routeId", "This route's vehicle is full (" + cap + " seats).");
        }
        return rt;
    }

    public Map<String, Object> allocate(AllocBody b) {
        Map<String, String> err = new LinkedHashMap<>();
        if (b.studentId() == null || count("select count(*) from students where id = ? and institution_id = ? and active", b.studentId(), inst()) == 0) err.put("studentId", "Choose a student.");
        Rt rt = checkAlloc(b, null, err);
        if (rt != null && err.isEmpty() && b.studentId() != null) {
            List<String> ex = jdbc.queryForList("select r.name from school_trn_allocations a join transport_routes r on r.id = a.route_id where a.student_id = ? and a.trip_type = ?", String.class, b.studentId(), rt.type());
            if (!ex.isEmpty()) err.put("studentId", "This student already has a " + ("PICKUP".equals(rt.type()) ? "pickup" : "drop-off") + " allocation on " + ex.get(0) + ". Change it from the table.");
        }
        fail(err);
        Long id = jdbc.queryForObject("insert into school_trn_allocations (institution_id, student_id, route_id, stop_id, trip_type, created_by) values (?, ?, ?, ?, ?, ?) returning id", Long.class, inst(), b.studentId(), b.routeId(), b.stopId(), rt.type(), actor());
        audit.record("TRANSPORT_ALLOCATED", "ALLOCATION", id, inst(), "Student allocated to " + rt.name());
        return Map.of("id", id);
    }

    public Map<String, Object> reallocate(Long id, AllocBody b) {
        List<String> cur = jdbc.queryForList("select trip_type from school_trn_allocations where id = ? and institution_id = ?", String.class, id, inst());
        if (cur.isEmpty()) throw ApiException.notFound("That allocation wasn't found.");
        Map<String, String> err = new LinkedHashMap<>();
        Rt rt = checkAlloc(b, id, err);
        if (rt != null && !rt.type().equals(cur.get(0))) err.put("routeId", "Choose a " + ("PICKUP".equals(cur.get(0)) ? "pickup" : "drop-off") + " route.");
        fail(err);
        jdbc.update("update school_trn_allocations set route_id = ?, stop_id = ? where id = ?", b.routeId(), b.stopId(), id);
        audit.record("TRANSPORT_REALLOCATED", "ALLOCATION", id, inst(), "Allocation changed to " + rt.name());
        return Map.of("id", id);
    }

    public void deallocate(Long id) {
        if (jdbc.update("delete from school_trn_allocations where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That allocation wasn't found.");
        audit.record("TRANSPORT_DEALLOCATED", "ALLOCATION", id, inst(), "Transport allocation removed");
    }

    // ------------------------------------------------------------------ trip board (updated by hand — no GPS)

    @Transactional(readOnly = true)
    public Live live(Long routeId) {
        ownRoute(routeId);
        List<Live> l = jdbc.query("select r.id, r.name, r.trip_type, v.vehicle_no, t.id is not null, coalesce(t.status, 'SCHEDULED'), coalesce(t.delay_minutes, 0), t.current_stop_id, t.updated_at at time zone 'Asia/Kolkata' from transport_routes r left join transport_vehicles v on v.id = r.vehicle_id left join school_trn_trips t on t.route_id = r.id and t.trip_date = ? where r.id = ?",
                (rs, n) -> { java.sql.Timestamp u = rs.getTimestamp(9); return new Live(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getString(6), rs.getInt(7), (Long) rs.getObject(8), u == null ? null : u.toLocalDateTime().toLocalTime().format(H12), null, List.of()); }, Date.valueOf(today()), routeId);
        Live v = l.get(0);
        List<Stop> st = stops(routeId);
        int curSeq = v.currentStopId() == null ? 0 : st.stream().filter((s) -> s.id().equals(v.currentStopId())).mapToInt(Stop::sequence).findFirst().orElse(0);
        List<StopLive> sl = st.stream().map((s) -> new StopLive(s.id(), s.name(), s.sequence(), s.arrival(), s.sequence() < curSeq, s.id().equals(v.currentStopId()))).toList();
        String eta = null;
        if (!st.isEmpty() && st.get(st.size() - 1).arrival() != null && !"CANCELLED".equals(v.status())) eta = LocalTime.parse(st.get(st.size() - 1).arrival()).plusMinutes("DELAYED".equals(v.status()) ? v.delayMinutes() : 0).format(H12);
        return new Live(v.routeId(), v.route(), v.tripType(), v.vehicle(), v.hasTrip(), v.status(), v.delayMinutes(), v.currentStopId(), v.updatedAt(), eta, sl);
    }

    public Live updateTrip(Long routeId, TripBody b) {
        List<String> rs = jdbc.queryForList("select status from transport_routes where id = ? and institution_id = ?", String.class, routeId, inst());
        if (rs.isEmpty()) throw ApiException.notFound("That route wasn't found.");
        Map<String, String> err = new LinkedHashMap<>();
        String st = b.status() == null ? "" : b.status().trim();
        if (!has(TRIP_STATUSES, st)) err.put("status", "Choose a status.");
        int delay = b.delayMinutes() == null ? 0 : b.delayMinutes();
        if ("DELAYED".equals(st)) { if (delay < 1 || delay > 600) err.put("delayMinutes", "Enter the delay in minutes (1 to 600)."); } else delay = "COMPLETED".equals(st) ? delay : 0;
        if (delay < 0 || delay > 600) err.put("delayMinutes", "Enter the delay in minutes (0 to 600).");
        if (b.currentStopId() != null && count("select count(*) from transport_stops where id = ? and route_id = ?", b.currentStopId(), routeId) == 0) err.put("currentStopId", "This stop isn't on the route.");
        if ("ACTIVE".equals(rs.get(0)) == false) err.put("status", "This route isn't active.");
        fail(err);
        List<Boolean> old = jdbc.queryForList("select delayed from school_trn_trips where route_id = ? and trip_date = ?", Boolean.class, routeId, Date.valueOf(today()));
        boolean delayed = "DELAYED".equals(st) || ("COMPLETED".equals(st) && !old.isEmpty() && old.get(0));
        if ("COMPLETED".equals(st) && !delayed) delay = 0;
        jdbc.update("insert into school_trn_trips (institution_id, route_id, trip_date, status, delayed, delay_minutes, current_stop_id, updated_by, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, now()) "
                + "on conflict (route_id, trip_date) do update set status = excluded.status, delayed = excluded.delayed, delay_minutes = excluded.delay_minutes, current_stop_id = excluded.current_stop_id, updated_by = excluded.updated_by, updated_at = now()",
                inst(), routeId, Date.valueOf(today()), st, delayed, delay, b.currentStopId(), actor());
        audit.record("TRIP_UPDATED", "ROUTE", routeId, inst(), "Trip status " + st);
        return live(routeId);
    }

    // ------------------------------------------------------------------ transport staff

    @Transactional(readOnly = true)
    public List<StaffRow> staff(String role) {
        List<StaffRow> out = new ArrayList<>();
        if (role == null || role.isBlank() || "DRIVER".equals(role))
            out.addAll(jdbc.query("select d.id, d.name, d.mobile, d.license_no, d.license_expiry, v.id, v.vehicle_no, d.status from transport_drivers d left join transport_vehicles v on v.driver_id = d.id where d.institution_id = ? order by d.name",
                    (rs, n) -> new StaffRow("D" + rs.getLong(1), "DRIVER", rs.getLong(1), rs.getString(2), "Driver", rs.getString(3), rs.getString(4), rs.getString(5), (Long) rs.getObject(6), rs.getString(7), rs.getString(8)), inst()));
        if (role == null || role.isBlank() || "ATTENDANT".equals(role))
            out.addAll(jdbc.query("select a.id, a.name, a.phone, v.id, v.vehicle_no, a.status from school_trn_attendants a left join transport_vehicles v on v.id = a.vehicle_id where a.institution_id = ? order by a.name",
                    (rs, n) -> new StaffRow("A" + rs.getLong(1), "ATTENDANT", rs.getLong(1), rs.getString(2), "Attendant", rs.getString(3), null, null, (Long) rs.getObject(4), rs.getString(5), rs.getString(6)), inst()));
        return out;
    }

    private void checkStaff(StaffBody b, boolean driver, Long selfId, Map<String, String> err) {
        if (clip(b.name(), 100) == null) err.put("name", "Enter the name.");
        maxLen(err, "name", b.name(), 100, "The name");
        String ph = clip(b.phone(), 20);
        if (ph == null) err.put("phone", "Enter a phone number."); else if (!PHONE.matcher(ph).matches()) err.put("phone", "Enter a valid phone number.");
        String st = b.status() == null || b.status().isBlank() ? "ACTIVE" : b.status().trim();
        if (!st.equals("ACTIVE") && !st.equals("INACTIVE")) err.put("status", "Choose Active or Inactive.");
        if (b.vehicleId() != null) {
            List<String> v = jdbc.queryForList("select status from transport_vehicles where id = ? and institution_id = ?", String.class, b.vehicleId(), inst());
            if (v.isEmpty()) err.put("vehicleId", "Choose a vehicle from this school.");
            else if (!"ACTIVE".equals(v.get(0))) err.put("vehicleId", "This vehicle isn't active.");
            else if (driver) {
                List<String> other = jdbc.queryForList("select d.name from transport_vehicles v join transport_drivers d on d.id = v.driver_id where v.id = ? and v.driver_id <> ?", String.class, b.vehicleId(), selfId == null ? -1L : selfId);
                if (!other.isEmpty()) err.put("vehicleId", "This vehicle already has a driver (" + other.get(0) + ").");
            }
        }
        if (driver) {
            String lic = clip(b.licenseNo(), 40);
            if (lic == null) err.put("licenseNo", "Enter the licence number.");
            else if (count("select count(*) from transport_drivers where institution_id = ? and lower(license_no) = ? and id <> ?", inst(), lic.toLowerCase(Locale.ROOT), selfId == null ? -1L : selfId) > 0) err.put("licenseNo", "This licence number is already registered.");
            maxLen(err, "licenseNo", b.licenseNo(), 40, "The licence number");
            LocalDate e = date(b.licenseExpiry(), "licenseExpiry", err, true);
            if (e != null && e.isBefore(today())) err.put("licenseExpiry", "This licence has expired.");
        }
    }

    public Map<String, Object> addStaff(StaffBody b) {
        boolean driver = "DRIVER".equals(b.role());
        if (!driver && !"ATTENDANT".equals(b.role())) throw new FieldErrorsException(Map.of("role", "Choose Driver or Attendant."));
        Map<String, String> err = new LinkedHashMap<>();
        checkStaff(b, driver, null, err); fail(err);
        boolean active = b.status() == null || b.status().isBlank() || "ACTIVE".equals(b.status().trim());
        Long id;
        if (driver) {
            Long max = jdbc.queryForObject("select coalesce(max(substring(code from '[0-9]+$')::int), 0) from transport_drivers where institution_id = ? and code ~ '^DRV-[0-9]+$'", Long.class, inst());
            id = jdbc.queryForObject("insert into transport_drivers (institution_id, code, name, mobile, license_no, license_expiry, status) values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class,
                    inst(), String.format("DRV-%03d", (max == null ? 0 : max) + 1), b.name().trim(), b.phone().trim(), b.licenseNo().trim(), Date.valueOf(LocalDate.parse(b.licenseExpiry().trim())), active ? "ACTIVE" : "INACTIVE");
            if (b.vehicleId() != null && active) jdbc.update("update transport_vehicles set driver_id = ? where id = ?", id, b.vehicleId());
        } else {
            id = jdbc.queryForObject("insert into school_trn_attendants (institution_id, name, phone, vehicle_id, status) values (?, ?, ?, ?, ?) returning id", Long.class, inst(), b.name().trim(), b.phone().trim(), b.vehicleId(), active ? "ACTIVE" : "INACTIVE");
        }
        audit.record("TRANSPORT_STAFF_ADDED", "TRANSPORT_STAFF", id, inst(), (driver ? "Driver" : "Attendant") + " added - " + b.name().trim());
        return Map.of("id", id);
    }

    public Map<String, Object> updateStaff(String kind, Long id, StaffBody b) {
        boolean driver = "DRIVER".equals(kind);
        if (!driver && !"ATTENDANT".equals(kind)) throw ApiException.badRequest("Unknown staff kind.");
        if (count("select count(*) from " + (driver ? "transport_drivers" : "school_trn_attendants") + " where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That staff member wasn't found.");
        Map<String, String> err = new LinkedHashMap<>();
        checkStaff(b, driver, id, err); fail(err);
        boolean active = b.status() == null || b.status().isBlank() || "ACTIVE".equals(b.status().trim());
        if (driver) {
            jdbc.update("update transport_drivers set name = ?, mobile = ?, license_no = ?, license_expiry = ?, status = ? where id = ?", b.name().trim(), b.phone().trim(), b.licenseNo().trim(), Date.valueOf(LocalDate.parse(b.licenseExpiry().trim())), active ? "ACTIVE" : "INACTIVE", id);
            jdbc.update("update transport_vehicles set driver_id = null where driver_id = ?", id);
            if (b.vehicleId() != null && active) jdbc.update("update transport_vehicles set driver_id = ? where id = ?", id, b.vehicleId());
        } else jdbc.update("update school_trn_attendants set name = ?, phone = ?, vehicle_id = ?, status = ? where id = ?", b.name().trim(), b.phone().trim(), b.vehicleId(), active ? "ACTIVE" : "INACTIVE", id);
        audit.record("TRANSPORT_STAFF_UPDATED", "TRANSPORT_STAFF", id, inst(), "Transport staff updated - " + b.name().trim());
        return Map.of("id", id);
    }

    public void deleteStaff(String kind, Long id) {
        boolean driver = "DRIVER".equals(kind);
        if (!driver && !"ATTENDANT".equals(kind)) throw ApiException.badRequest("Unknown staff kind.");
        if (jdbc.update("delete from " + (driver ? "transport_drivers" : "school_trn_attendants") + " where id = ? and institution_id = ?", id, inst()) == 0) throw ApiException.notFound("That staff member wasn't found.");
        audit.record("TRANSPORT_STAFF_DELETED", "TRANSPORT_STAFF", id, inst(), "Transport staff deleted");
    }

    // ------------------------------------------------------------------ reports

    @Transactional(readOnly = true)
    public Table report(String type, String fromS, String toS, Long routeId) {
        if (routeId != null) ownRoute(routeId);
        String rf = routeId == null ? "" : " and r.id = ?";
        String scope = routeId == null ? "All routes" : jdbc.queryForObject("select name from transport_routes where id = ?", String.class, routeId);
        List<List<String>> rows = new ArrayList<>();
        switch (type == null ? "" : type) {
            case "PUNCTUALITY" -> {
                Map<String, String> err = new LinkedHashMap<>();
                LocalDate from = date(fromS, "from", err, true), to = date(toS, "to", err, true); fail(err);
                if (to.isBefore(from)) throw ApiException.badRequest("The end date can't be before the start date.");
                if (from.plusDays(400).isBefore(to)) throw ApiException.badRequest("Choose a range of at most 13 months.");
                List<Object> a = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to), inst())); if (routeId != null) a.add(routeId);
                long[] tot = new long[4];
                jdbc.query("select r.name, r.trip_type, count(t.id) filter (where t.status in ('ON_TIME', 'DELAYED', 'COMPLETED')), count(t.id) filter (where t.delayed and t.status in ('DELAYED', 'COMPLETED')), count(t.id) filter (where t.status = 'CANCELLED'), "
                        + "coalesce(round(avg(t.delay_minutes) filter (where t.delayed and t.delay_minutes > 0)), 0) from transport_routes r left join school_trn_trips t on t.route_id = r.id and t.trip_date between ? and ? where r.institution_id = ?" + rf + " group by r.id, r.name, r.trip_type order by r.name", (rs) -> {
                    long counted = rs.getLong(3), delayed = rs.getLong(4), cancelled = rs.getLong(5), onTime = counted - delayed;
                    tot[0] += counted; tot[1] += onTime; tot[2] += delayed; tot[3] += cancelled;
                    rows.add(List.of(rs.getString(1), "PICKUP".equals(rs.getString(2)) ? "Pickup" : "Drop-off", String.valueOf(counted), String.valueOf(onTime), String.valueOf(delayed), String.valueOf(rs.getLong(5)), counted == 0 ? "—" : pct(onTime, counted) + "%", delayed == 0 ? "—" : rs.getLong(6) + " min"));
                }, a.toArray());
                return new Table("Trip Punctuality", scope + " · " + from + " to " + to, List.of("Route", "Trip", "Trips Logged", "On Time", "Delayed", "Cancelled", "Punctuality", "Avg Delay"), List.of(false, false, true, true, true, true, true, true), rows,
                        List.of("Total", "", String.valueOf(tot[0]), String.valueOf(tot[1]), String.valueOf(tot[2]), String.valueOf(tot[3]), tot[0] == 0 ? "—" : pct(tot[1], tot[0]) + "%", ""),
                        "Trips come from the status you set on Live Tracking each day; a day with no update isn't counted.");
            }
            case "OCCUPANCY" -> {
                List<Object> a = new ArrayList<>(List.of(inst())); if (routeId != null) a.add(routeId);
                jdbc.query("select r.name, r.trip_type, coalesce(v.vehicle_no, '—'), v.seating_capacity, (select count(*) from school_trn_allocations x where x.route_id = r.id) from transport_routes r left join transport_vehicles v on v.id = r.vehicle_id where r.institution_id = ?" + rf + " order by r.name", (rs) -> {
                    Integer cap = (Integer) rs.getObject(4); long used = rs.getLong(5);
                    rows.add(List.of(rs.getString(1), "PICKUP".equals(rs.getString(2)) ? "Pickup" : "Drop-off", rs.getString(3), cap == null ? "—" : String.valueOf(cap), String.valueOf(used), cap == null ? "—" : pct(used, cap) + "%", cap == null ? "—" : String.valueOf(Math.max(0, cap - used))));
                }, a.toArray());
                return new Table("Route Occupancy", scope, List.of("Route", "Trip", "Vehicle", "Seats", "Allocated", "Occupancy", "Seats Left"), List.of(false, false, false, true, true, true, true), rows, null,
                        "Occupancy is allocated students against seating capacity. Actual boardings aren't recorded, so there is no average or peak.");
            }
            case "RIDERSHIP" -> {
                List<Object> a = new ArrayList<>(List.of(inst())); if (routeId != null) a.add(routeId);
                long[] tot = new long[1];
                jdbc.query("select r.name, r.trip_type, (select count(*) from school_trn_allocations x where x.route_id = r.id) from transport_routes r where r.institution_id = ?" + rf + " order by r.name", (rs) -> {
                    tot[0] += rs.getLong(3);
                    rows.add(List.of(rs.getString(1), "PICKUP".equals(rs.getString(2)) ? "Pickup" : "Drop-off", String.valueOf(rs.getLong(3))));
                }, a.toArray());
                long unique = routeId == null ? count("select count(distinct student_id) from school_trn_allocations where institution_id = ?", inst()) : count("select count(distinct student_id) from school_trn_allocations where route_id = ?", routeId);
                return new Table("Student Ridership", scope, List.of("Route", "Trip", "Students"), List.of(false, false, true), rows, List.of("Allocations", "", String.valueOf(tot[0])),
                        unique + " different student" + (unique == 1 ? "" : "s") + " use transport. A student with both pickup and drop-off allocations appears on two routes.");
            }
            default -> throw ApiException.badRequest("Choose a report.");
        }
    }
}
