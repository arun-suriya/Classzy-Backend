package com.centram.classzy.academic.classes;

import com.centram.classzy.common.TenantEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "sections")
public class Section extends TenantEntity {

    private Long schoolClassId;
    private String name;
    private Long teacherId;
    private String room;
    private Integer maxStudents;
    private boolean active = true;
    private String description;

    public Long getSchoolClassId() { return schoolClassId; }
    public void setSchoolClassId(Long schoolClassId) { this.schoolClassId = schoolClassId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
