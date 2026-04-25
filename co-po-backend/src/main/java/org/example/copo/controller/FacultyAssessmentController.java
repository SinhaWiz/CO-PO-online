package org.example.copo.controller;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.service.FacultyAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty/assessments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class FacultyAssessmentController {

    private final FacultyAssessmentService assessmentService;

    @PreAuthorize("hasRole('FACULTY')")
    @GetMapping("/questions/{assessmentId}")
    public ResponseEntity<List<AssessmentQuestion>> getQuestionsForAssessment(@PathVariable Integer assessmentId) {
        return ResponseEntity.ok(assessmentService.getQuestionsForAssessment(assessmentId));
    }

    @PreAuthorize("hasRole('FACULTY')")
    @PostMapping("/questions")
    public ResponseEntity<AssessmentQuestion> createQuestion(@RequestBody AssessmentQuestion question) {
        return ResponseEntity.ok(assessmentService.createQuestion(question));
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
    public ResponseEntity<StudentAssessmentMarks> saveStudentMarks(@RequestBody StudentAssessmentMarks marks) {
        return ResponseEntity.ok(assessmentService.saveStudentMarks(marks));
    }
}
