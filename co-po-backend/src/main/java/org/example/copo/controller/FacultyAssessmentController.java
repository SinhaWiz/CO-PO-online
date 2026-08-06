package org.example.copo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.StudentAssessmentMarks;
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
}
