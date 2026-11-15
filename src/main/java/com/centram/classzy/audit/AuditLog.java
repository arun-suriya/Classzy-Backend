package com.centram.classzy.audit;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant createdAt = Instant.now();
    private String actorEmail;
    private String actorRole;
    private String action;
    private String entityType;
    private Long entityId;
    private Long institutionId;
    private String description;
    private String ipAddress;
    private String userAgent;
    private String oldValue;
    private String newValue;

    protected AuditLog() {
    }

    public AuditLog(String actorEmail, String actorRole, String action, String entityType, Long entityId,
                    Long institutionId, String description) {
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.institutionId = institutionId;
        this.description = description.length() > 500 ? description.substring(0, 500) : description;
    }

    /** Where the request came from and, optionally, the value before and after a change. */
    void capture(String ip, String agent, String oldValue, String newValue) {
        this.ipAddress = ip;
        this.userAgent = agent == null ? null : (agent.length() > 300 ? agent.substring(0, 300) : agent);
        this.oldValue = oldValue == null ? null : (oldValue.length() > 500 ? oldValue.substring(0, 500) : oldValue);
        this.newValue = newValue == null ? null : (newValue.length() > 500 ? newValue.substring(0, 500) : newValue);
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getActorEmail() { return actorEmail; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public Long getInstitutionId() { return institutionId; }
    public String getDescription() { return description; }
}
