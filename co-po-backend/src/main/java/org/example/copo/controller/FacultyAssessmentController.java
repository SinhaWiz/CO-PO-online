package org.example.copo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.service.AssessmentService;
import org.example.copo.service.CourseAssessmentSectionService;
import org.example.copo.service.FacultyAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty/assessments")
@RequiredArgsConstructor
public class FacultyAssessmentController {

    private final FacultyAssessmentService assessmentService;
    private final CourseAssessmentSectionService sectionService;
    private final AssessmentService instanceService;

    public record ResolveInstanceRequest(@NotNull Integer sectionId, @NotBlank String academicYear) {}

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/questions/{assessmentId}")
    public ResponseEntity<List<FacultyAssessmentService.QuestionDto>> getQuestionsForAssessment(@PathVariable Integer assessmentId) {
        return ResponseEntity.ok(assessmentService.getQuestionsForAssessment(assessmentId));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/questions")
    public ResponseEntity<FacultyAssessmentService.QuestionDto> createQuestion(
        @Valid @RequestBody FacultyAssessmentService.CreateQuestionRequest request
    ) {
        return ResponseEntity.ok(assessmentService.createQuestion(request));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Integer questionId) {
        assessmentService.deleteQuestion(questionId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/marks/question/{questionId}")
    public ResponseEntity<List<StudentAssessmentMarks>> getMarksForQuestion(@PathVariable Integer questionId) {
        return ResponseEntity.ok(assessmentService.getMarksForQuestion(questionId));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/marks")
    public ResponseEntity<StudentAssessmentMarks> saveStudentMarks(@Valid @RequestBody StudentAssessmentMarks marks) {
        return ResponseEntity.ok(assessmentService.saveStudentMarks(marks));
    }

    // Read-only faculty access to a course's admin-defined sections - faculty need to
    // see these to know what they're grading, even though only admins can edit them.
    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/sections/{courseCode}/{programme}")
    public ResponseEntity<List<CourseAssessmentSection>> getSectionsForCourse(
        @PathVariable String courseCode, @PathVariable String programme
    ) {
        return ResponseEntity.ok(sectionService.getSections(courseCode, programme));
    }

    // Get-or-create: resolves a (section, academic year) pair to a real Assessment id,
    // creating the instance the first time anyone touches that section for that year.
    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/instances")
    public ResponseEntity<Assessment> resolveAssessmentInstance(@Valid @RequestBody ResolveInstanceRequest request) {
        return ResponseEntity.ok(instanceService.getOrCreate(request.sectionId(), request.academicYear()));
    }
}
