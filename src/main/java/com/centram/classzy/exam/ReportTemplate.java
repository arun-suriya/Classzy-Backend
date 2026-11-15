package com.centram.classzy.exam;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "report_templates")
public class ReportTemplate extends TenantEntity {
    public Long academicYearId;
    public String name;
    public String reportType;      // TERM_WISE, ANNUAL, PERIODIC, CERTIFICATE
    public Integer classFrom;
    public Integer classTo;
    public String sections = "";   // comma separated section codes
    public boolean active = true;
}
