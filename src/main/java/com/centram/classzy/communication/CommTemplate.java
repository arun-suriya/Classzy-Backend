package com.centram.classzy.communication;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "comm_templates")
public class CommTemplate extends TenantEntity {
    public String channel;
    public String name;
    public String category = "GENERAL";
    public String title;
    public String body;
    public boolean active = true;
    public java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();
}
