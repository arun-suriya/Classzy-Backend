package com.centram.classzy.communication;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The list of every communication setting: its section, type, default and limits.
 * Settings are stored as key/value rows, so adding a setting is one line here (plus the UI control).
 */
public final class CommSpec {

    public enum Type { BOOL, INT, TEXT, OPT, TIME, EMAIL, EMAILS, CSV, SECRET, TZ, COLOR, URL, DEC, REF }

    public record Spec(String key, Type type, String def, boolean required, int min, int max, List<String> allowed) {}

    /** What the API returns instead of a stored secret. Sending it back (or blank) keeps the stored secret. */
    public static final String MASK = "********";

    static final List<String> WEEKDAYS = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    static final List<String> LANGUAGES = List.of("en", "hi", "ta", "te", "kn", "ml", "mr", "bn", "gu", "pa", "ur");
    static final List<String> CATEGORIES = List.of("ADMISSIONS", "ATTENDANCE", "EXAMINATIONS", "FEES", "TRANSPORT", "LIBRARY",
            "ANNOUNCEMENTS", "EVENTS", "MARKETING", "EMERGENCY", "GENERAL");
    static final List<String> COUNTRY_CODES = List.of("+91", "+1", "+44", "+971", "+65", "+61", "+977", "+94", "+880", "+966");

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Map<String, Map<String, Spec>> SECTIONS = new LinkedHashMap<>();

    private CommSpec() {}

    public static Map<String, Spec> section(String name) {
        return SECTIONS.get(name);
    }

    /** Cleans one submitted value. Returns null for "keep what is stored" (blank or masked secret). Throws with the message to show. */
    public static String clean(Spec s, String raw) {
        String v = raw == null ? "" : raw.trim();
        if (s.type == Type.SECRET) {
            if (v.isEmpty() || v.equals(MASK)) return null;
            if (v.length() > 300) throw new IllegalArgumentException("Too long.");
            return v;
        }
        if (v.isEmpty()) {
            if (s.required) throw new IllegalArgumentException("Required.");
            return "";
        }
        switch (s.type) {
            case BOOL -> {
                if (!v.equals("true") && !v.equals("false")) throw new IllegalArgumentException("Must be true or false.");
                return v;
            }
            case INT -> {
                try {
                    int n = Integer.parseInt(v);
                    if (n < s.min || n > s.max) throw new IllegalArgumentException("Enter a number from " + s.min + " to " + s.max + ".");
                    return String.valueOf(n);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Enter a whole number.");
                }
            }
            case TEXT -> {
                if (v.length() > s.max) throw new IllegalArgumentException("At most " + s.max + " characters.");
                return v;
            }
            case OPT -> {
                if (!s.allowed.contains(v)) throw new IllegalArgumentException("Choose one of the listed options.");
                return v;
            }
            case TIME -> {
                if (!TIME.matcher(v).matches()) throw new IllegalArgumentException("Use the HH:mm format.");
                return v;
            }
            case EMAIL -> {
                if (v.length() > 120 || !EMAIL.matcher(v).matches()) throw new IllegalArgumentException("Enter a valid email address.");
                return v;
            }
            case EMAILS -> {
                List<String> out = new ArrayList<>();
                for (String p : v.split(",")) {
                    String e = p.trim();
                    if (e.isEmpty()) continue;
                    if (!EMAIL.matcher(e).matches()) throw new IllegalArgumentException(e + " is not a valid email address.");
                    out.add(e);
                }
                if (out.size() > 10) throw new IllegalArgumentException("At most 10 addresses.");
                return String.join(",", new LinkedHashSet<>(out));
            }
            case CSV -> {
                Set<String> out = new LinkedHashSet<>();
                for (String p : v.split(",")) {
                    String e = p.trim();
                    if (e.isEmpty()) continue;
                    if (!s.allowed.contains(e)) throw new IllegalArgumentException(e + " is not a valid choice.");
                    out.add(e);
                }
                return String.join(",", out);
            }
            case TZ -> {
                if (!ZoneId.getAvailableZoneIds().contains(v)) throw new IllegalArgumentException("Unknown time zone.");
                return v;
            }
            case COLOR -> {
                if (!v.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Use a colour like #0066FF.");
                return v.toUpperCase();
            }
            case URL -> {
                if (v.length() > 200 || !v.matches("(?i)https?://[^\\s]+")) throw new IllegalArgumentException("Enter a valid http(s) address.");
                return v;
            }
            case DEC -> {
                try {
                    java.math.BigDecimal n = new java.math.BigDecimal(v);
                    if (n.scale() > 2 || n.compareTo(java.math.BigDecimal.valueOf(s.min)) < 0 || n.compareTo(java.math.BigDecimal.valueOf(s.max)) > 0)
                        throw new IllegalArgumentException("Enter an amount from " + s.min + " to " + s.max + " (up to 2 decimals).");
                    return n.stripTrailingZeros().toPlainString();
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Enter a valid amount.");
                }
            }
            case REF -> {
                if (!v.matches("\\d{1,18}")) throw new IllegalArgumentException("Choose a valid item.");
                return v;
            }
            default -> throw new IllegalStateException();
        }
    }

    // ------------------------------------------------------------------ definitions

    private static final class Sec {
        final Map<String, Spec> m = new LinkedHashMap<>();

        private Sec put(Spec s) { m.put(s.key, s); return this; }
        Sec bool(String k, boolean d) { return put(new Spec(k, Type.BOOL, String.valueOf(d), false, 0, 0, List.of())); }
        Sec num(String k, int d, int min, int max) { return put(new Spec(k, Type.INT, String.valueOf(d), false, min, max, List.of())); }
        Sec text(String k, String d, int max, boolean req) { return put(new Spec(k, Type.TEXT, d, req, 0, max, List.of())); }
        Sec opt(String k, String d, List<String> allowed) { return put(new Spec(k, Type.OPT, d, false, 0, 0, allowed)); }
        Sec time(String k, String d) { return put(new Spec(k, Type.TIME, d, false, 0, 0, List.of())); }
        Sec email(String k, boolean req) { return put(new Spec(k, Type.EMAIL, "", req, 0, 0, List.of())); }
        Sec emails(String k, String d) { return put(new Spec(k, Type.EMAILS, d, false, 0, 0, List.of())); }
        Sec csv(String k, String d, List<String> allowed) { return put(new Spec(k, Type.CSV, d, false, 0, 0, allowed)); }
        Sec secret(String k, boolean req) { return put(new Spec(k, Type.SECRET, "", req, 0, 0, List.of())); }
        Sec tz(String k, String d) { return put(new Spec(k, Type.TZ, d, false, 0, 0, List.of())); }
        Sec color(String k, String d) { return put(new Spec(k, Type.COLOR, d, false, 0, 0, List.of())); }
        Sec url(String k, boolean req) { return put(new Spec(k, Type.URL, "", req, 0, 0, List.of())); }
        Sec dec(String k, String d, int min, int max) { return put(new Spec(k, Type.DEC, d, false, min, max, List.of())); }
        Sec ref(String k) { return put(new Spec(k, Type.REF, "", false, 0, 0, List.of())); }
    }

    private static Sec sec(String name) {
        Sec s = new Sec();
        SECTIONS.put(name, s.m);
        return s;
    }

    private static final String WORK_DAYS = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
    private static final List<String> SEND_TIMES = List.of("IMMEDIATE", "SCHEDULED");
    private static final String NOTIFY_CATS = "ADMISSIONS,ATTENDANCE,EXAMINATIONS,FEES,TRANSPORT,LIBRARY,ANNOUNCEMENTS,EVENTS";

    static {
        // ---- email
        sec("EMAIL").bool("enabled", true).email("defaultFrom", true).email("replyTo", false)
                .opt("language", "en", LANGUAGES).bool("includeLogo", true).bool("includeUnsubscribe", true)
                .bool("trackOpens", true).bool("trackClicks", true).csv("categories", NOTIFY_CATS + ",MARKETING", CATEGORIES);
        sec("SMTP").opt("provider", "SENDGRID", List.of("SENDGRID", "AWS_SES", "GMAIL", "MICROSOFT_365", "CUSTOM"))
                .text("host", "", 120, true).num("port", 587, 1, 65535).bool("ssl", true)
                .text("username", "", 120, true).secret("password", true);
        sec("SENDER").text("senderName", "", 100, true).email("senderEmail", true).text("address", "", 300, false)
                .email("contactEmail", false).text("contactPhone", "", 20, false);
        sec("SIGNATURE").text("content", "", 2000, false).opt("position", "BOTTOM", List.of("BOTTOM", "TOP"))
                .bool("includeLogo", true).bool("includeSocial", true).bool("includeDisclaimer", false).bool("includeAll", true)
                .text("facebook", "", 200, false).text("twitter", "", 200, false).text("instagram", "", 200, false).text("linkedin", "", 200, false);
        sec("EMAIL_DELIVERY").opt("sendTime", "IMMEDIATE", SEND_TIMES).tz("timezone", "Asia/Kolkata").bool("businessHoursOnly", false)
                .bool("delayNonUrgent", true).num("delayMinutes", 10, 1, 1440).bool("batchSimilar", true).bool("sendDigest", true)
                .bool("limitPerDay", true).num("maxPerDay", 10, 1, 500).bool("retryFailed", true).num("maxRetries", 3, 0, 10)
                .num("retryInterval", 15, 1, 1440).bool("copyAdmin", false).bool("quietEnabled", true).time("quietStart", "22:00")
                .time("quietEnd", "06:00").csv("quietDays", WORK_DAYS, WEEKDAYS);

        // ---- SMS
        sec("SMS").bool("enabled", true).opt("language", "en", LANGUAGES).opt("countryCode", "+91", COUNTRY_CODES)
                .opt("encoding", "UTF8", List.of("UTF8", "GSM7")).opt("messageType", "TRANSACTIONAL", List.of("TRANSACTIONAL", "PROMOTIONAL", "OTP"))
                .csv("categories", NOTIFY_CATS + ",EMERGENCY", CATEGORIES);
        sec("SMS_PROVIDER").opt("provider", "TWILIO", List.of("TWILIO", "MSG91", "TEXTLOCAL", "AWS_SNS", "CUSTOM"))
                .text("accountSid", "", 100, true).secret("authToken", true).text("fromNumber", "", 20, true)
                .text("apiUrl", "", 200, false).num("timeoutSeconds", 30, 5, 120).bool("webhookEnabled", false).text("webhookUrl", "", 200, false);
        sec("SMS_DELIVERY").opt("sendTime", "IMMEDIATE", SEND_TIMES).time("allowedFrom", "08:00").time("allowedTo", "20:00")
                .tz("timezone", "Asia/Kolkata").bool("businessHoursOnly", false).bool("delayNonUrgent", true).num("delayMinutes", 10, 1, 1440)
                .num("maxRetries", 3, 0, 10).num("retryInterval", 15, 1, 1440).bool("retryTemporary", true).num("stopAfterHours", 24, 1, 168)
                .num("maxPerRecipientDay", 10, 1, 100).num("maxPerBatch", 5000, 1, 100000);
        sec("SMS_REPORTS").bool("enabled", true).bool("store", true).bool("notifyFailure", true).bool("summaryToAdmin", false)
                .emails("adminEmails", "").csv("notifyFor", "FAILED_SMS,LOW_BALANCE,PROVIDER_DOWNTIME",
                        List.of("FAILED_SMS", "LOW_BALANCE", "HIGH_VOLUME", "PROVIDER_DOWNTIME", "DAILY_SUMMARY"));

        // ---- transport (general settings tab of Transport Settings)
        sec("TRANSPORT").bool("enabled", true).opt("provider", "IN_HOUSE", List.of("IN_HOUSE", "THIRD_PARTY"))
                .opt("gpsProvider", "CLASSZY_GPS", List.of("CLASSZY_GPS", "THIRD_PARTY", "NONE")).num("stopRadius", 100, 10, 1000)
                .num("trackingInterval", 30, 5, 600).opt("parentAccess", "LIVE_TRACKING", List.of("LIVE_TRACKING", "ETA_ONLY", "NONE"))
                .bool("sosAlert", true).bool("notifyPickDrop", true).bool("speedAlert", true).num("speedLimit", 60, 10, 150)
                .bool("geofenceAlert", true).bool("routeDeviationAlert", true).csv("workingDays", WORK_DAYS + ",SATURDAY", WEEKDAYS)
                .num("pickupWindow", 15, 1, 120).num("dropWindow", 15, 1, 120);

        // ---- library
        sec("LIBRARY").bool("enabled", true).text("libraryName", "Main Library", 100, true).text("libraryCode", "LIB-001", 30, true)
                .opt("libraryType", "PHYSICAL_DIGITAL", List.of("PHYSICAL", "DIGITAL", "PHYSICAL_DIGITAL"))
                .time("weekFrom", "08:00").time("weekTo", "17:00").time("satFrom", "09:00").time("satTo", "13:00")
                .opt("sunday", "CLOSED", List.of("CLOSED", "OPEN")).time("sunFrom", "09:00").time("sunTo", "13:00")
                .num("loanDays", 14, 1, 365).num("maxBooksStudent", 5, 1, 50).num("maxBooksTeacher", 10, 1, 50).num("inactivityDays", 30, 1, 365)
                .bool("reservation", true).bool("ebooks", true).bool("barcode", true).bool("interBranch", false).bool("digitalApproval", true).bool("showInPortal", true);
        sec("LIB_FINE").bool("enabled", true).bool("onlinePayment", true).bool("overdueNotify", true).bool("graceEnabled", true)
                .num("reminder1", 1, 0, 365).num("reminder2", 3, 0, 365).num("finalReminder", 7, 0, 365).num("blockAfter", 30, 1, 365);

        // ---- security & access (stored settings; enforcement is not wired yet)
        sec("SEC_AUTH").bool("mfa", false).csv("methods", "PASSWORD,GOOGLE,MICROSOFT,MOBILE_OTP", List.of("PASSWORD", "GOOGLE", "MICROSOFT", "MOBILE_OTP", "BIOMETRIC"))
                .opt("ssoProvider", "NONE", List.of("NONE", "GOOGLE", "MICROSOFT", "CUSTOM")).text("ssoClientId", "", 200, false)
                .secret("ssoClientSecret", false).url("ssoRedirectUrl", false);
        sec("SEC_PASSWORD").num("minLength", 8, 6, 64).bool("requireUpper", true).bool("requireLower", true).bool("requireNumber", true)
                .bool("requireSpecial", true).bool("blockCommon", true).bool("expiryEnabled", true).num("expiryDays", 90, 7, 730)
                .bool("reuseEnabled", true).num("reuseCount", 5, 1, 24).num("maxFailed", 5, 3, 20).num("lockoutMinutes", 30, 1, 1440)
                .bool("selfReset", true).bool("resetEmail", true).bool("resetSms", true).bool("resetApproval", false);
        sec("SEC_SESSION").bool("restrictIp", false).bool("restrictCountry", false).bool("restrictTime", false).time("timeFrom", "06:00").time("timeTo", "22:00")
                .bool("trustedOnly", false).bool("captcha", true).bool("notifyAdmin", true).num("timeoutMinutes", 120, 5, 1440).num("rememberDays", 7, 1, 90);

        // ---- branding
        sec("BRAND_APPEARANCE").color("primary", "#0066FF").color("secondary", "#28A745").color("accent", "#FFA500").color("text", "#333333")
                .opt("font", "INTER", List.of("INTER", "POPPINS", "ROBOTO", "OPEN_SANS", "LATO")).opt("fontSize", "MEDIUM", List.of("SMALL", "MEDIUM", "LARGE"))
                .bool("rounded", true).bool("compactHeader", true).bool("darkMode", false);
        sec("BRAND_LOGIN").bool("showLogo", true).bool("showName", true).bool("showBackground", true).bool("showRemember", true).bool("showForgot", true)
                .bool("showLanguage", true).text("title", "", 100, false).text("subtitle", "Enter your credentials to continue", 150, false);

        // ---- push
        List<String> sounds = List.of("DEFAULT", "CHIME", "BELL", "SILENT");
        sec("PUSH").bool("enabled", true).bool("allowCustomize", true).bool("importantOnly", false).text("senderName", "Classzy", 60, false)
                .opt("deepLink", "OPEN_APP", List.of("OPEN_APP", "OPEN_WEB", "NONE")).opt("sound", "DEFAULT", sounds)
                .bool("allRolesDefault", false).bool("includeLogo", true).bool("badgeCount", true).bool("richNotifications", true)
                .bool("trackOpens", true).bool("allowScheduling", true);
        sec("PUSH_MOBILE").bool("enabled", true).bool("promptPermission", true).bool("inAppCenter", true).bool("lockScreenPreview", true)
                .opt("sound", "DEFAULT", sounds).opt("vibration", "DEFAULT", List.of("DEFAULT", "SHORT", "LONG", "NONE"))
                .opt("badge", "UNREAD", List.of("UNREAD", "ALL", "NONE")).bool("silentEnabled", true).time("silentStart", "22:00").time("silentEnd", "06:00");
        sec("PUSH_WEB").bool("enabled", true).bool("inAppCenter", true).bool("popups", true).bool("desktop", true).bool("playSound", true)
                .opt("sound", "DEFAULT", sounds).num("durationSeconds", 8, 1, 60)
                .opt("position", "TOP_RIGHT", List.of("TOP_RIGHT", "TOP_LEFT", "BOTTOM_RIGHT", "BOTTOM_LEFT"));
        sec("PUSH_DELIVERY").opt("sendMode", "IMMEDIATE", SEND_TIMES).time("sendTime", "10:00").tz("timezone", "Asia/Kolkata")
                .opt("audience", "ALL", List.of("ALL", "ROLES", "CLASSES", "USERS")).num("maxPerDay", 10, 1, 200).num("minInterval", 5, 0, 1440)
                .bool("avoidDuplicates", true).bool("limitSilent", true);
    }
}
