package com.centram.classzy.communication;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_categories")
public class PushCategory extends TenantEntity {
    public String name;
    public String description;
    public boolean active = true;
    public boolean mobile = true;
    public boolean web = true;
}
