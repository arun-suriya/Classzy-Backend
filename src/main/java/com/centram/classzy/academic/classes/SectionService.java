package com.centram.classzy.academic.classes;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.centram.classzy.academic.classes.SectionDto.Request;
import com.centram.classzy.academic.classes.SectionDto.Response;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.people.StudentRepository;
import com.centram.classzy.people.Teacher;
import com.centram.classzy.people.TeacherRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectionService {

    private final SectionRepository repo;
    private final SchoolClassService classes;
    private final TeacherRepository teachers;
    private final StudentRepository students;
    private final CurrentUser current;

    public SectionService(SectionRepository repo, SchoolClassService classes, TeacherRepository teachers,
                          StudentRepository students, CurrentUser current) {
        this.repo = repo;
        this.classes = classes;
        this.teachers = teachers;
        this.students = students;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<Response> list(Long classId) {
        SchoolClass c = classes.require(classId);
        return repo.findByInstitutionIdAndSchoolClassIdOrderByNameAsc(current.institutionId(), classId)
                .stream().map(s -> toResponse(s, c, teacherNames())).toList();
    }

    @Transactional
    public Response create(Request r) {
        var s = new Section();
        s.setInstitutionId(current.institutionId());
        return respond(save(s, r));
    }

    @Transactional
    public Response update(Long id, Request r) {
        return respond(save(require(id), r));
    }

    @Transactional
    public void delete(Long id) {
        Section s = require(id);
        if (students.countBySectionId(id) > 0) {
            throw ApiException.conflict("Section " + s.getName() + " still has students. Move them first.");
        }
        repo.delete(s);
        repo.flush();
    }

    private Section require(Long id) {
        return repo.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Section"));
    }

    private Section save(Section s, Request r) {
        SchoolClass c = classes.require(r.schoolClassId());
        long id = s.getId() == null ? -1L : s.getId();
        String name = r.name().trim();

        if (repo.existsBySchoolClassIdAndNameIgnoreCaseAndIdNot(c.getId(), name, id)) {
            throw ApiException.conflict("Section " + name + " already exists in " + c.getName() + ".");
        }
        if (r.teacherId() != null) {
            teachers.findByIdAndInstitutionId(r.teacherId(), current.institutionId())
                    .orElseThrow(() -> ApiException.notFound("Teacher"));
        }
        if (r.maxStudents() != null && id != -1L && students.countBySectionId(id) > r.maxStudents()) {
            throw ApiException.conflict("Max Students can't be lower than the current " + students.countBySectionId(id) + " students.");
        }

        s.setSchoolClassId(c.getId());
        s.setName(name);
        s.setTeacherId(r.teacherId());
        s.setRoom(r.room() == null || r.room().isBlank() ? null : r.room().trim());
        s.setMaxStudents(r.maxStudents());
        s.setActive(r.active());
        s.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        return repo.saveAndFlush(s);
    }

    private Response respond(Section s) {
        return toResponse(s, classes.require(s.getSchoolClassId()), teacherNames());
    }

    private Map<Long, String> teacherNames() {
        return teachers.findByInstitutionIdOrderByFullNameAsc(current.institutionId()).stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getFullName, (a, b) -> a));
    }

    private Response toResponse(Section s, SchoolClass c, Map<Long, String> teacherNames) {
        return new Response(s.getId(), s.getSchoolClassId(), c.getName(), s.getName(), s.getTeacherId(),
                s.getTeacherId() == null ? null : teacherNames.get(s.getTeacherId()), s.getRoom(),
                s.getMaxStudents(), s.isActive(), s.getDescription(), students.countBySectionId(s.getId()));
    }
}
