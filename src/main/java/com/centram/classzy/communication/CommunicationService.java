package com.centram.classzy.communication;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.centram.classzy.audit.AuditService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.common.FieldErrorsException;
import com.centram.classzy.communication.CommDto.TestResult;
import com.centram.classzy.communication.CommSpec.Spec;
import com.centram.classzy.communication.CommSpec.Type;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email, SMS and push settings (one section = one group of fields on a tab). */
@Service
@Transactional
public class CommunicationService {

    private final CurrentUser current;
    private final CommSettingsStore store;
    private final AuditService audit;

    public CommunicationService(CurrentUser current, CommSettingsStore store, AuditService audit) {
        this.current = current;
        this.store = store;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, String> get(String section) {
        return view(requireSection(section), store.load(current.institutionId(), section));
    }

    public Map<String, String> save(String section, Map<String, String> body) {
        Long inst = current.institutionId();
        Map<String, Spec> specs = requireSection(section);
        Map<String, String> stored = store.load(inst, section);
        Map<String, String> err = new LinkedHashMap<>();
        Map<String, String> clean = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : body.entrySet()) {
            Spec sp = specs.get(e.getKey());
            if (sp == null) { err.put(e.getKey(), "Unknown setting."); continue; }
            try {
                String v = CommSpec.clean(sp, e.getValue());
                if (v != null) clean.put(sp.key(), v);
            } catch (IllegalArgumentException ex) {
                err.put(sp.key(), ex.getMessage());
            }
        }
        // required fields must be filled once this save is applied
        for (Spec sp : specs.values()) {
            if (!sp.required() || err.containsKey(sp.key())) continue;
            String merged = clean.containsKey(sp.key()) ? clean.get(sp.key()) : stored.getOrDefault(sp.key(), sp.def());
            if (merged == null || merged.isBlank()) err.put(sp.key(), "Required.");
        }
        if (!err.isEmpty()) throw new FieldErrorsException(err);
        clean.forEach((k, v) -> store.put(inst, section, k, v));
        audit.record("COMM_SETTINGS_UPDATED", "CommunicationSettings", inst, inst, section + " communication settings updated");
        return view(specs, store.load(inst, section));
    }

    private Map<String, Spec> requireSection(String section) {
        Map<String, Spec> s = CommSpec.section(section);
        if (s == null) throw ApiException.notFound("Settings section");
        return s;
    }

    /** Defaults overlaid with what is stored; secrets are never returned. */
    private Map<String, String> view(Map<String, Spec> specs, Map<String, String> stored) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Spec sp : specs.values()) {
            String v = stored.get(sp.key());
            if (sp.type() == Type.SECRET) out.put(sp.key(), v == null || v.isBlank() ? "" : CommSpec.MASK);
            else out.put(sp.key(), v == null ? sp.def() : v);
        }
        return out;
    }

    // ------------------------------------------------------------------ connection test

    /** Checks that the saved server can be reached over TCP. Credentials are not verified. */
    @Transactional(readOnly = true)
    public TestResult testConnection(String kind) {
        Long inst = current.institutionId();
        if ("smtp".equals(kind)) {
            Map<String, String> v = store.load(inst, "SMTP");
            return probe(v.getOrDefault("host", ""), parsePort(v.get("port"), 587));
        }
        if ("sms-provider".equals(kind)) {
            String url = store.load(inst, "SMS_PROVIDER").getOrDefault("apiUrl", "");
            if (url.isBlank()) return new TestResult(false, "Save the API URL first.");
            try {
                URI u = URI.create(url);
                if (u.getHost() == null) return new TestResult(false, "The API URL is not valid.");
                int port = u.getPort() != -1 ? u.getPort() : "http".equalsIgnoreCase(u.getScheme()) ? 80 : 443;
                return probe(u.getHost(), port);
            } catch (IllegalArgumentException e) {
                return new TestResult(false, "The API URL is not valid.");
            }
        }
        throw ApiException.notFound("Connection test");
    }

    private static int parsePort(String v, int fallback) {
        try { return v == null || v.isBlank() ? fallback : Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }

    private static TestResult probe(String host, int port) {
        return NetProbe.probe(host, port);
    }
}
