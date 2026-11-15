package com.centram.classzy.exam;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "exam_types")
public class ExamType extends TenantEntity {
    public Long academicYearId;
    public String name;
    public String code;
    public String description;
    public Integer classFrom;
    public Integer classTo;
    public int weightage;
    public String category = "INTERNAL";   // INTERNAL, EXTERNAL, PRACTICAL
    public boolean active = true;
    public int sortOrder;
}
