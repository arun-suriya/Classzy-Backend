package com.centram.classzy.academic.classes;

import java.util.List;

import com.centram.classzy.academic.classes.ClassDto.Request;
import com.centram.classzy.academic.classes.ClassDto.Response;
import com.centram.classzy.academic.year.AcademicYearService;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.people.StudentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolClassService {

    private final SchoolClassRepository repo;
    private final SectionRepository sections;
    private final StudentRepository students;
    private final AcademicYearService years;
    private final CurrentUser current;

    public SchoolClassService(SchoolClassRepository repo, SectionRepository sections, StudentRepository students,
                              AcademicYearService years, CurrentUser current) {
        this.repo = repo;
        this.sections = sections;
        this.students = students;
        this.years = years;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<Response> list(Long academicYearId) {
        years.require(academicYearId);
        return repo.findByInstitutionIdAndAcademicYearIdOrderByGradeLevelAscNameAsc(current.institutionId(), academicYearId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public Response create(Request r) {
        var c = new SchoolClass();
        c.setInstitutionId(current.institutionId());
        return toResponse(save(c, r));
    }

    @Transactional
    public Response update(Long id, Request r) {
        return toResponse(save(require(id), r));
    }

    @Transactional
    public void delete(Long id) {
        SchoolClass c = require(id);
        if (sections.countBySchoolClassId(id) > 0) {
            throw ApiException.conflict("Remove the sections of " + c.getName() + " before deleting the class.");
        }
        if (students.countBySchoolClassId(id) > 0) {
            throw ApiException.conflict(c.getName() + " still has students. Move them to another class first.");
        }
        repo.delete(c);
        repo.flush();
    }

    /** Used by SectionService to validate a class belongs to the current institution. */
    @Transactional(readOnly = true)
    public SchoolClass require(Long id) {
        return repo.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Class"));
    }

    private SchoolClass save(SchoolClass c, Request r) {
        years.require(r.academicYearId());
        long id = c.getId() == null ? -1L : c.getId();
        String name = r.name().trim();
        if (repo.existsByAcademicYearIdAndNameIgnoreCaseAndIdNot(r.academicYearId(), name, id)) {
            throw ApiException.conflict(name + " already exists in this academic year.");
        }
        c.setAcademicYearId(r.academicYearId());
        c.setName(name);
        c.setGradeLevel(r.gradeLevel());
        c.setActive(r.active());
        c.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        return repo.saveAndFlush(c);
    }

    private Response toResponse(SchoolClass c) {
        return new Response(c.getId(), c.getAcademicYearId(), c.getName(), c.getGradeLevel(), c.isActive(),
                c.getDescription(), students.countBySchoolClassId(c.getId()), sections.countBySchoolClassId(c.getId()));
    }
}
