package org.example.copo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.CO;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.PO;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.service.AssessmentService;
import org.example.copo.service.CourseAssessmentSectionService;
import org.example.copo.service.CourseOutcomeService;
import org.example.copo.service.FacultyAssessmentService;
import org.example.copo.service.OutcomeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty/assessments")
@RequiredArgsConstructor
public class FacultyAssessmentController {

    private final FacultyAssessmentService assessmentService;
    private final CourseAssessmentSectionService sectionService;
    private final AssessmentService instanceService;
    private final OutcomeService outcomeService;
    private final CourseOutcomeService courseOutcomeService;

    public record ResolveInstanceRequest(@NotNull Integer sectionId, @NotBlank String academicYear) {}

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/questions/{assessmentId}")
    public ResponseEntity<List<FacultyAssessmentService.QuestionDto>> getQuestionsForAssessment(
        Authentication authentication, @PathVariable Integer assessmentId
    ) {
        return ResponseEntity.ok(assessmentService.getQuestionsForAssessment(authentication.getName(), assessmentId));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/questions")
    public ResponseEntity<FacultyAssessmentService.QuestionDto> createQuestion(
        Authentication authentication, @Valid @RequestBody FacultyAssessmentService.CreateQuestionRequest request
    ) {
        return ResponseEntity.ok(assessmentService.createQuestion(authentication.getName(), request));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<FacultyAssessmentService.QuestionDto> updateQuestion(
        Authentication authentication, @PathVariable Integer questionId,
        @Valid @RequestBody FacultyAssessmentService.UpdateQuestionRequest request
    ) {
        return ResponseEntity.ok(assessmentService.updateQuestion(authentication.getName(), questionId, request));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(Authentication authentication, @PathVariable Integer questionId) {
        assessmentService.deleteQuestion(authentication.getName(), questionId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/marks/question/{questionId}")
    public ResponseEntity<List<StudentAssessmentMarks>> getMarksForQuestion(
        Authentication authentication, @PathVariable Integer questionId
    ) {
        return ResponseEntity.ok(assessmentService.getMarksForQuestion(authentication.getName(), questionId));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/marks")
    public ResponseEntity<StudentAssessmentMarks> saveStudentMarks(
        Authentication authentication, @Valid @RequestBody StudentAssessmentMarks marks
    ) {
        return ResponseEntity.ok(assessmentService.saveStudentMarks(authentication.getName(), marks));
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

    // Read-only faculty access to the CO/PO master lists and a course's allow-list -
    // these lived only under /api/admin/**, which faculty tokens are locked out of at
    // the security filter chain, so a faculty-facing CO/PO picker had nothing to call.
    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/outcomes/co")
    public ResponseEntity<List<CO>> getAllCOs() {
        return ResponseEntity.ok(outcomeService.getAllCOs());
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/outcomes/po")
    public ResponseEntity<List<PO>> getAllPOs() {
        return ResponseEntity.ok(outcomeService.getAllPOs());
    }

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/outcomes/{courseCode}/{programme}")
    public ResponseEntity<CourseOutcomeService.CourseOutcomesDto> getCourseOutcomes(
        @PathVariable String courseCode, @PathVariable String programme
    ) {
        return ResponseEntity.ok(courseOutcomeService.getCourseOutcomes(courseCode, programme));
    }
}
