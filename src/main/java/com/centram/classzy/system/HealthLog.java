package com.centram.classzy.system;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "health_logs")
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant checkedAt = Instant.now();
    private String overall;
    private boolean applicationOk;
    private boolean databaseOk;
    private Integer databaseMs;
    private boolean storageOk;
    private Integer memoryUsedPct;
    private Integer diskFreePct;
    private String detail;

    protected HealthLog() {
    }

    public HealthLog(String overall, boolean applicationOk, boolean databaseOk, Integer databaseMs, boolean storageOk,
                     Integer memoryUsedPct, Integer diskFreePct, String detail) {
        this.overall = overall;
        this.applicationOk = applicationOk;
        this.databaseOk = databaseOk;
        this.databaseMs = databaseMs;
        this.storageOk = storageOk;
        this.memoryUsedPct = memoryUsedPct;
        this.diskFreePct = diskFreePct;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getOverall() { return overall; }
    public boolean isApplicationOk() { return applicationOk; }
    public boolean isDatabaseOk() { return databaseOk; }
    public Integer getDatabaseMs() { return databaseMs; }
    public boolean isStorageOk() { return storageOk; }
    public Integer getMemoryUsedPct() { return memoryUsedPct; }
    public Integer getDiskFreePct() { return diskFreePct; }
    public String getDetail() { return detail; }
}
