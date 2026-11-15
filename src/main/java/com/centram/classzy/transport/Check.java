package com.centram.classzy.transport;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Small field-validation helpers shared by the transport services. */
final class Check {
    private Check() {}

    static final Set<String> ACTIVE_STATES = Set.of("ACTIVE", "INACTIVE");

    static String trim(String s) { return s == null ? "" : s.trim(); }

    static String orNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    static LocalDate date(Map<String, String> err, String field, String v, boolean required) {
        if (v == null || v.isBlank()) {
            if (required) err.put(field, "Required.");
            return null;
        }
        try { return LocalDate.parse(v.trim()); } catch (DateTimeParseException e) { err.put(field, "Enter a valid date."); return null; }
    }

    static LocalTime time(Map<String, String> err, String field, String v) {
        if (v == null || v.isBlank()) return null;
        try { return LocalTime.parse(v.trim()); } catch (DateTimeParseException e) { err.put(field, "Use the HH:mm format."); return null; }
    }

    static void max(Map<String, String> err, String field, String v, int max) {
        if (v != null && v.trim().length() > max) err.put(field, "At most " + max + " characters.");
    }

    static void oneOf(Map<String, String> err, String field, String v, Set<String> allowed) {
        if (v == null || !allowed.contains(v)) err.put(field, "Choose one of the listed options.");
    }
}
