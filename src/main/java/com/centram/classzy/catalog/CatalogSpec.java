package com.centram.classzy.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.centram.classzy.communication.CommSpec.Spec;
import com.centram.classzy.communication.CommSpec.Type;

/** What each kind of catalog item looks like. Attribute keys starting with "_" are set by the server only. */
final class CatalogSpec {
    private CatalogSpec() {}

    record Seed(String name, String code, Map<String, String> attrs) {}

    record Kind(String label, boolean codeRequired, String autoPrefix, boolean nameIsFixedList, Map<String, Spec> attrs, List<Seed> seeds) {}

    static final List<String> MEMBER_TYPES = List.of("Student", "Teacher", "Staff", "External Member");

    private static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    private static Map<String, Spec> attrs(Spec... specs) {
        Map<String, Spec> m = new LinkedHashMap<>();
        for (Spec s : specs) m.put(s.key(), s);
        return m;
    }
    private static Spec text(String k, int max) { return new Spec(k, Type.TEXT, "", false, 0, max, List.of()); }
    private static Spec opt(String k, String d, List<String> allowed) { return new Spec(k, Type.OPT, d, false, 0, 0, allowed); }
    private static Spec bool(String k, boolean d) { return new Spec(k, Type.BOOL, String.valueOf(d), false, 0, 0, List.of()); }
    private static Spec num(String k, int d, int min, int max) { return new Spec(k, Type.INT, String.valueOf(d), false, min, max, List.of()); }
    private static Spec dec(String k, String d, int min, int max) { return new Spec(k, Type.DEC, d, false, min, max, List.of()); }
    private static Seed seed(String name, String code, String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return new Seed(name, code, m);
    }

    static {
        KINDS.put("LIB_CATEGORY", new Kind("Book category", false, "CAT-", false,
                attrs(text("description", 300), new Spec("parent", Type.REF, "", false, 0, 0, List.of())),
                List.of(seed("Text Books", null, "description", "Curriculum text books"), seed("Reference Books", null, "description", "Dictionaries, encyclopedias and other reference works"),
                        seed("Fiction", null, "description", "Novels and story books"), seed("Non-Fiction", null, "description", "Biographies, science and general knowledge"),
                        seed("Magazines", null, "description", "Magazines and periodicals"), seed("New Arrivals", null, "description", "Recently added books"),
                        seed("E-Books", null, "description", "Digital books"))));
        KINDS.put("LIB_STATUS", new Kind("Book status", true, null, false,
                attrs(text("description", 300), new Spec("color", Type.COLOR, "#16A34A", false, 0, 0, List.of())),
                List.of(seed("Available", "AVL", "description", "On the shelf and can be issued", "color", "#16A34A"),
                        seed("Issued", "ISS", "description", "Currently issued to a member", "color", "#2563EB"),
                        seed("Reserved", "RES", "description", "Reserved by a member", "color", "#F97316"),
                        seed("Under Maintenance", "MNT", "description", "Being repaired or re-bound", "color", "#FACC15"),
                        seed("Lost", "LST", "description", "Reported lost", "color", "#EF4444"),
                        seed("Discarded", "DSC", "description", "Removed from the collection", "color", "#9CA3AF"))));
        KINDS.put("LIB_ISSUE_RULE", new Kind("Issue rule", false, null, true,
                attrs(num("maxBooks", 5, 1, 50), num("loanDays", 14, 1, 365), bool("canRenew", true), num("maxRenewals", 2, 0, 10), bool("approvalRequired", false)),
                List.of(seed("Student", null, "maxBooks", "5", "loanDays", "14", "canRenew", "true", "maxRenewals", "2", "approvalRequired", "false"),
                        seed("Teacher", null, "maxBooks", "10", "loanDays", "30", "canRenew", "true", "maxRenewals", "3", "approvalRequired", "false"),
                        seed("Staff", null, "maxBooks", "8", "loanDays", "30", "canRenew", "true", "maxRenewals", "3", "approvalRequired", "false"),
                        seed("External Member", null, "maxBooks", "3", "loanDays", "7", "canRenew", "true", "maxRenewals", "1", "approvalRequired", "true"))));
        KINDS.put("LIB_FINE_RULE", new Kind("Fine rule", false, null, true,
                attrs(opt("fineType", "PER_DAY", List.of("PER_DAY", "FLAT")), dec("amount", "5", 0, 100000), num("graceDays", 2, 0, 90), dec("maxFine", "200", 0, 1000000)),
                List.of(seed("Student", null, "fineType", "PER_DAY", "amount", "5", "graceDays", "2", "maxFine", "200"),
                        seed("Teacher", null, "fineType", "PER_DAY", "amount", "10", "graceDays", "2", "maxFine", "500"),
                        seed("Staff", null, "fineType", "PER_DAY", "amount", "10", "graceDays", "2", "maxFine", "500"),
                        seed("External Member", null, "fineType", "PER_DAY", "amount", "20", "graceDays", "1", "maxFine", "1000"))));
        KINDS.put("CUSTOM_FIELD", new Kind("Custom field", true, null, false,
                attrs(opt("dataType", "TEXT", List.of("TEXT", "NUMBER", "DATE", "DROPDOWN", "YES_NO")),
                        opt("appliesTo", "STUDENT", List.of("STUDENT", "STAFF", "PARENT", "GENERAL")),
                        bool("mandatory", false), bool("showInPortal", false), text("options", 500)),
                List.of()));
        KINDS.put("INTEGRATION", new Kind("Integration", false, null, false,
                attrs(opt("category", "OTHER", List.of("PAYMENT", "SMS", "EMAIL", "VIDEO", "STORAGE", "IDENTITY", "ANALYTICS", "OTHER")),
                        text("provider", 60), new Spec("baseUrl", Type.URL, "", false, 0, 0, List.of()), text("accountId", 120),
                        new Spec("secret", Type.SECRET, "", false, 0, 0, List.of()), text("notes", 300)),
                List.of()));
    }

    static Kind of(String kind) { return KINDS.get(kind); }
}
