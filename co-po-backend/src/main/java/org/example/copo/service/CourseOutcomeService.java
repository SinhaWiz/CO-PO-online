package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.CourseCO;
import org.example.copo.entity.CoursePO;
import org.example.copo.repository.CourseCORepository;
import org.example.copo.repository.CoursePORepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * The subset of COs/POs a given course actually uses. Question-level CO/PO mapping
 * (Phase 2) validates against this allow-list rather than the full master list.
 */
@Service
@RequiredArgsConstructor
public class CourseOutcomeService {

    private final CourseCORepository courseCORepository;
    private final CoursePORepository coursePORepository;

    public record CourseOutcomesDto(List<Integer> coIds, List<Integer> poIds) {}

    @Transactional(readOnly = true)
    public CourseOutcomesDto getCourseOutcomes(String courseCode, String programme) {
        List<Integer> coIds = courseCORepository.findByCourseCodeAndProgramme(courseCode, programme)
            .stream().map(CourseCO::getCoId).toList();
        List<Integer> poIds = coursePORepository.findByCourseCodeAndProgramme(courseCode, programme)
            .stream().map(CoursePO::getPoId).toList();
        return new CourseOutcomesDto(coIds, poIds);
    }

    @Transactional
    public CourseOutcomesDto updateCourseOutcomes(String courseCode, String programme, List<Integer> coIds, List<Integer> poIds) {
        courseCORepository.deleteByCourseCodeAndProgramme(courseCode, programme);
        coursePORepository.deleteByCourseCodeAndProgramme(courseCode, programme);

        Set<Integer> uniqueCoIds = coIds == null ? Set.of() : Set.copyOf(coIds);
        Set<Integer> uniquePoIds = poIds == null ? Set.of() : Set.copyOf(poIds);

        for (Integer coId : uniqueCoIds) {
            courseCORepository.save(new CourseCO(courseCode, programme, coId));
        }
        for (Integer poId : uniquePoIds) {
            coursePORepository.save(new CoursePO(courseCode, programme, poId));
        }

        return new CourseOutcomesDto(List.copyOf(uniqueCoIds), List.copyOf(uniquePoIds));
    }
}
