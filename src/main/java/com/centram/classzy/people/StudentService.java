package com.centram.classzy.people;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.centram.classzy.academic.classes.SchoolClass;
import com.centram.classzy.academic.classes.SchoolClassRepository;
import com.centram.classzy.academic.classes.Section;
import com.centram.classzy.academic.classes.SectionRepository;
import com.centram.classzy.common.ApiException;
import com.centram.classzy.common.CurrentUser;
import com.centram.classzy.people.StudentDto.Request;
import com.centram.classzy.people.StudentDto.Response;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

    private final StudentRepository repo;
    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final CurrentUser current;

    public StudentService(StudentRepository repo, SchoolClassRepository classes, SectionRepository sections,
                          CurrentUser current) {
        this.repo = repo;
        this.classes = classes;
        this.sections = sections;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<Response> list(Long classId, Long sectionId) {
        Long inst = current.institutionId();
        List<Student> all = classId == null
                ? repo.findByInstitutionIdOrderByFullNameAsc(inst)
                : repo.findByInstitutionIdAndSchoolClassIdOrderByFullNameAsc(inst, classId);
        if (sectionId != null) {
            all = all.stream().filter(s -> sectionId.equals(s.getSectionId())).toList();
        }
        return toResponses(all);
    }

    @Transactional
    public Response create(Request r) {
        var s = new Student();
        s.setInstitutionId(current.institutionId());
        return toResponses(List.of(save(s, r))).get(0);
    }

    @Transactional
    public Response update(Long id, Request r) {
        return toResponses(List.of(save(require(id), r))).get(0);
    }

    @Transactional
    public void delete(Long id) {
        repo.delete(require(id));
        repo.flush();
    }

    private Student require(Long id) {
        return repo.findByIdAndInstitutionId(id, current.institutionId())
                .orElseThrow(() -> ApiException.notFound("Student"));
    }

    private Student save(Student s, Request r) {
        Long inst = current.institutionId();
        long id = s.getId() == null ? -1L : s.getId();
        String admissionNo = r.admissionNo().trim();

        if (repo.existsByInstitutionIdAndAdmissionNoIgnoreCaseAndIdNot(inst, admissionNo, id)) {
            throw ApiException.conflict("Admission number " + admissionNo + " is already in use.");
        }
        if (r.sectionId() != null && r.schoolClassId() == null) {
            throw ApiException.badRequest("Select a class before choosing a section.");
        }
        if (r.schoolClassId() != null) {
            classes.findByIdAndInstitutionId(r.schoolClassId(), inst).orElseThrow(() -> ApiException.notFound("Class"));
        }
        if (r.sectionId() != null) {
            Section sec = sections.findByIdAndInstitutionId(r.sectionId(), inst)
                    .orElseThrow(() -> ApiException.notFound("Section"));
            if (!sec.getSchoolClassId().equals(r.schoolClassId())) {
                throw ApiException.badRequest("That section does not belong to the selected class.");
            }
            boolean alreadyHere = Objects.equals(s.getSectionId(), sec.getId());
            if (sec.getMaxStudents() != null && !alreadyHere && repo.countBySectionId(sec.getId()) >= sec.getMaxStudents()) {
                throw ApiException.conflict("Section " + sec.getName() + " is full (max " + sec.getMaxStudents() + " students).");
            }
        }

        s.setAdmissionNo(admissionNo);
        s.setFullName(r.fullName().trim());
        s.setSchoolClassId(r.schoolClassId());
        s.setSectionId(r.sectionId());
        s.setActive(r.active());
        return repo.saveAndFlush(s);
    }

    private List<Response> toResponses(List<Student> students) {
        Set<Long> classIds = students.stream().map(Student::getSchoolClassId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> sectionIds = students.stream().map(Student::getSectionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> classNames = new HashMap<>();
        classes.findAllById(classIds).forEach((SchoolClass c) -> classNames.put(c.getId(), c.getName()));
        Map<Long, String> sectionNames = new HashMap<>();
        sections.findAllById(sectionIds).forEach((Section sec) -> sectionNames.put(sec.getId(), sec.getName()));
        return students.stream().map(s -> new Response(s.getId(), s.getAdmissionNo(), s.getFullName(),
                s.getSchoolClassId(), classNames.get(s.getSchoolClassId()),
                s.getSectionId(), sectionNames.get(s.getSectionId()), s.isActive())).toList();
    }
}
