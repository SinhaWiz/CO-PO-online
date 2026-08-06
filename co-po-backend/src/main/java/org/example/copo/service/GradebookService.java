package org.example.copo.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.Student;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.example.copo.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The gradebook: one call to load a whole section's roster x questions grid
 * (pre-populated with existing marks, like the desktop app's grid), one call to save
 * a full pass of edits. Replaces having to save one student/one question at a time.
 */
@Service
@RequiredArgsConstructor
public class GradebookService {

    private final AssessmentRepository assessmentRepository;
    private final CourseAssessmentSectionRepository sectionRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final StudentAssessmentMarksRepository marksRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final AssignmentAuthorizationService authorizationService;

    public record RosterQuestionDto(Integer id, String title, Double maxMarks) {}
    public record RosterCellDto(Integer questionId, Double marksObtained) {}
    public record RosterRowDto(String studentId, String studentName, List<RosterCellDto> cells) {}
    public record RosterDto(List<RosterQuestionDto> questions, List<RosterRowDto> rows) {}

    public record MarkEntry(@NotBlank String studentId, @NotNull Integer questionId, @NotNull Double marksObtained) {}
    public record BatchSaveRequest(@NotEmpty @Valid List<MarkEntry> entries) {}
    public record BatchSaveResult(int saved, List<String> errors) {}

    public RosterDto getRoster(String facultyEmail, Integer assessmentId) {
        authorizationService.requireOwnsAssessment(facultyEmail, assessmentId);

        Assessment assessment = getAssessment(assessmentId);
        CourseAssessmentSection section = getSection(assessment.getSectionId());

        List<AssessmentQuestion> questions = questionRepository.findByAssessmentId(assessmentId);
        List<Enrollment> enrollments = enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(
            section.getCourseCode(), section.getProgramme(), assessment.getAcademicYear()
        );

        List<String> studentIds = enrollments.stream().map(Enrollment::getStudentId).distinct().sorted().toList();
        Map<String, String> studentNames = studentRepository.findAllById(studentIds).stream()
            .collect(Collectors.toMap(Student::getId, Student::getName));

        List<Integer> questionIds = questions.stream().map(AssessmentQuestion::getId).toList();
        Map<String, Double> marksByKey = questionIds.isEmpty()
            ? Map.of()
            : marksRepository.findByQuestionIdIn(questionIds).stream()
                .collect(Collectors.toMap(m -> markKey(m.getStudentId(), m.getQuestionId()), StudentAssessmentMarks::getMarksObtained));

        List<RosterRowDto> rows = studentIds.stream()
            .map(studentId -> new RosterRowDto(
                studentId,
                studentNames.getOrDefault(studentId, studentId),
                questions.stream()
                    .map(q -> new RosterCellDto(q.getId(), marksByKey.get(markKey(studentId, q.getId()))))
                    .toList()
            ))
            .toList();

        List<RosterQuestionDto> questionDtos = questions.stream()
            .map(q -> new RosterQuestionDto(q.getId(), q.getTitle(), q.getMarks()))
            .toList();

        return new RosterDto(questionDtos, rows);
    }

    @Transactional
    public BatchSaveResult saveBatch(String facultyEmail, Integer assessmentId, BatchSaveRequest request) {
        authorizationService.requireOwnsAssessment(facultyEmail, assessmentId);

        Map<Integer, AssessmentQuestion> questionsById = questionRepository.findByAssessmentId(assessmentId).stream()
            .collect(Collectors.toMap(AssessmentQuestion::getId, q -> q));

        int saved = 0;
        List<String> errors = new ArrayList<>();

        for (MarkEntry entry : request.entries()) {
            AssessmentQuestion question = questionsById.get(entry.questionId());
            if (question == null) {
                errors.add(entry.studentId() + "/" + entry.questionId() + ": question is not part of this assessment.");
                continue;
            }
            if (entry.marksObtained() < 0 || entry.marksObtained() > question.getMarks()) {
                errors.add(entry.studentId() + "/" + entry.questionId() + ": marks must be between 0 and " + question.getMarks() + ".");
                continue;
            }

            StudentAssessmentMarks existing = marksRepository
                .findByStudentIdAndQuestionId(entry.studentId(), entry.questionId())
                .orElse(null);
            if (existing != null) {
                existing.setMarksObtained(entry.marksObtained());
                marksRepository.save(existing);
            } else {
                StudentAssessmentMarks mark = new StudentAssessmentMarks();
                mark.setStudentId(entry.studentId());
                mark.setQuestionId(entry.questionId());
                mark.setMarksObtained(entry.marksObtained());
                marksRepository.save(mark);
            }
            saved++;
        }

        return new BatchSaveResult(saved, errors);
    }

    private Assessment getAssessment(Integer assessmentId) {
        return assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + assessmentId));
    }

    private CourseAssessmentSection getSection(Integer sectionId) {
        return sectionRepository.findById(sectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Assessment section not found: " + sectionId));
    }

    private String markKey(String studentId, Integer questionId) {
        return studentId + "||" + questionId;
    }
}
