package com.centram.classzy.exam;

import java.util.LinkedHashSet;
import java.util.Set;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "result_templates")
public class ResultTemplate extends TenantEntity {
    public Long academicYearId;
    public String name;
    public String calculationType;   // WEIGHTED, SUBJECT_BASED, BOARD_PATTERN
    public Integer classFrom;
    public Integer classTo;
    public String roundingRule = "NEAREST_WHOLE";
    public String displayFormat = "GRADE_AND_MARKS";
    public boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "result_template_exam_types", joinColumns = @JoinColumn(name = "result_template_id"))
    @Column(name = "exam_type_id")
    public Set<Long> examTypeIds = new LinkedHashSet<>();
}
