package com.centram.classzy.audit;

import java.time.Instant;
import java.util.List;

import com.centram.classzy.security.AppPrincipal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Writes the activity trail. A failure here must never break the action being logged. */
@Service
public class AuditService {

    public record Entry(Long id, Instant createdAt, String actorEmail, String actorRole, String action,
                        String entityType, Long entityId, Long institutionId, String description) {
        static Entry of(AuditLog a) {
            return new Entry(a.getId(), a.getCreatedAt(), a.getActorEmail(), a.getActorRole(), a.getAction(),
                    a.getEntityType(), a.getEntityId(), a.getInstitutionId(), a.getDescription());
        }
    }

    public record PageOf(List<Entry> items, long total, int page, int size) {
    }

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /** Records an action by whoever is signed in on this request. */
    public void record(String action, String entityType, Long entityId, Long institutionId, String description) {
        String email = null;
        String role = null;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
            email = p.email();
            role = p.role().name();
        }
        recordAs(email, role, action, entityType, entityId, institutionId, description);
    }

    public void recordAs(String email, String role, String action, String entityType, Long entityId,
                         Long institutionId, String description) {
        write(email, role, action, entityType, entityId, institutionId, description, null, null);
    }

    /** Same as record, with the value before and after the change (shown on the Audit Logs page). */
    public void recordChange(String action, String entityType, Long entityId, Long institutionId, String description, String oldValue, String newValue) {
        String email = null;
        String role = null;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
            email = p.email();
            role = p.role().name();
        }
        write(email, role, action, entityType, entityId, institutionId, description, oldValue, newValue);
    }

    private void write(String email, String role, String action, String entityType, Long entityId, Long institutionId,
                       String description, String oldValue, String newValue) {
        try {
            AuditLog row = new AuditLog(email, role, action, entityType, entityId, institutionId, description);
            String ip = null;
            String agent = null;
            if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                var req = sra.getRequest();
                String fwd = req.getHeader("X-Forwarded-For");
                String cand = fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr();
                ip = cand != null && cand.matches("[0-9a-fA-F:.]{3,45}") ? cand : null;
                agent = req.getHeader("User-Agent");
            }
            row.capture(ip, agent, oldValue, newValue);
            repo.save(row);
        } catch (RuntimeException e) {
            log.warn("Could not write audit log '{}': {}", action, e.getMessage());
        }
    }

    public List<Entry> recent(int n) {
        return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, n)).map(Entry::of).getContent();
    }

    public PageOf page(int page, int size, String action) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<AuditLog> result = action == null || action.isBlank()
                ? repo.findAllByOrderByCreatedAtDesc(pageable)
                : repo.findByActionOrderByCreatedAtDesc(action.trim().toUpperCase(), pageable);
        return new PageOf(result.map(Entry::of).getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
    }
}
