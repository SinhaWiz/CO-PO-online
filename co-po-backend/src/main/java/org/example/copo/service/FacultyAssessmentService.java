package org.example.copo.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.AssessmentQuestionPO;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.AssessmentQuestionPORepository;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.CourseCORepository;
import org.example.copo.repository.CoursePORepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacultyAssessmentService {

    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentQuestionPORepository questionPoRepository;
    private final AssessmentRepository assessmentRepository;
    private final CourseAssessmentSectionRepository sectionRepository;
    private final CourseCORepository courseCORepository;
    private final CoursePORepository coursePORepository;
    private final AssignmentAuthorizationService authorizationService;

    public record QuestionDto(Integer id, Integer assessmentId, String title, Double marks, Integer coId, List<Integer> poIds) {}

    public record CreateQuestionRequest(
        @NotNull Integer assessmentId,
        @NotBlank String title,
        @NotNull @Positive Double marks,
        Integer coId,
        List<Integer> poIds
    ) {}

    public record UpdateQuestionRequest(
        @NotBlank String title,
        @NotNull @Positive Double marks,
        Integer coId,
        List<Integer> poIds
    ) {}

    public List<QuestionDto> getQuestionsForAssessment(String facultyEmail, Integer assessmentId) {
        authorizationService.requireOwnsAssessment(facultyEmail, assessmentId);

        List<AssessmentQuestion> questions = questionRepository.findByAssessmentId(assessmentId);
        if (questions.isEmpty()) {
            return List.of();
        }

        List<Integer> questionIds = questions.stream().map(AssessmentQuestion::getId).toList();
        Map<Integer, List<Integer>> poIdsByQuestion = questionPoRepository.findByQuestionIdIn(questionIds).stream()
            .collect(Collectors.groupingBy(
                AssessmentQuestionPO::getQuestionId,
                Collectors.mapping(AssessmentQuestionPO::getPoId, Collectors.toList())
            ));

        return questions.stream()
            .map(q -> toDto(q, poIdsByQuestion.getOrDefault(q.getId(), List.of())))
            .toList();
    }

    @Transactional
    public QuestionDto createQuestion(String facultyEmail, CreateQuestionRequest request) {
        authorizationService.requireOwnsAssessment(facultyEmail, request.assessmentId());
        CourseAssessmentSection section = resolveSectionForAssessment(request.assessmentId());
        validateOutcomeMapping(section.getCourseCode(), section.getProgramme(), request.coId(), request.poIds());

        AssessmentQuestion question = new AssessmentQuestion();
        question.setAssessmentId(request.assessmentId());
        question.setTitle(request.title());
        question.setMarks(request.marks());
        question.setCoId(request.coId());
        AssessmentQuestion saved = questionRepository.save(question);

        List<Integer> poIds = normalizePoIds(request.poIds());
        savePoMappings(saved.getId(), poIds);

        return toDto(saved, poIds);
    }

    @Transactional
    public QuestionDto updateQuestion(String facultyEmail, Integer questionId, UpdateQuestionRequest request) {
        AssessmentQuestion question = questionRepository.findById(questionId)
            .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        authorizationService.requireOwnsAssessment(facultyEmail, question.getAssessmentId());
        CourseAssessmentSection section = resolveSectionForAssessment(question.getAssessmentId());
        validateOutcomeMapping(section.getCourseCode(), section.getProgramme(), request.coId(), request.poIds());

        question.setTitle(request.title());
        question.setMarks(request.marks());
        question.setCoId(request.coId());
        AssessmentQuestion saved = questionRepository.save(question);

        List<Integer> poIds = normalizePoIds(request.poIds());
        questionPoRepository.deleteByQuestionId(questionId);
        savePoMappings(questionId, poIds);

        return toDto(saved, poIds);
    }

    public void deleteQuestion(String facultyEmail, Integer questionId) {
        AssessmentQuestion question = questionRepository.findById(questionId)
            .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + questionId));
        authorizationService.requireOwnsAssessment(facultyEmail, question.getAssessmentId());
        // AssessmentQuestion_PO rows are ON DELETE CASCADE, no separate cleanup needed.
        questionRepository.deleteById(questionId);
    }

    private List<Integer> normalizePoIds(List<Integer> poIds) {
        return poIds == null ? List.of() : poIds.stream().distinct().toList();
    }

    private void savePoMappings(Integer questionId, List<Integer> poIds) {
        for (Integer poId : poIds) {
            questionPoRepository.save(new AssessmentQuestionPO(questionId, poId));
        }
    }

    private CourseAssessmentSection resolveSectionForAssessment(Integer assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + assessmentId));
        return sectionRepository.findById(assessment.getSectionId())
            .orElseThrow(() -> new ResourceNotFoundException("Assessment section not found: " + assessment.getSectionId()));
    }

    // Mirrors the desktop app's rule: a question can't have a PO without a CO or vice
    // versa, and whatever CO/POs are chosen must actually be in this course's allow-list
    // (Phase 1.2) rather than any CO/PO in the system.
    private void validateOutcomeMapping(String courseCode, String programme, Integer coId, List<Integer> poIds) {
        boolean hasCo = coId != null;
        boolean hasPo = poIds != null && !poIds.isEmpty();

        if (hasCo != hasPo) {
            throw new IllegalArgumentException("A question needs both a CO and at least one PO, or neither - not just one.");
        }

        if (hasCo && !courseCORepository.existsByCourseCodeAndProgrammeAndCoId(courseCode, programme, coId)) {
            throw new IllegalArgumentException("CO " + coId + " is not in this course's allowed outcomes.");
        }

        if (hasPo) {
            for (Integer poId : poIds) {
                if (!coursePORepository.existsByCourseCodeAndProgrammeAndPoId(courseCode, programme, poId)) {
                    throw new IllegalArgumentException("PO " + poId + " is not in this course's allowed outcomes.");
                }
            }
        }
    }

    private QuestionDto toDto(AssessmentQuestion question, List<Integer> poIds) {
        return new QuestionDto(
            question.getId(),
            question.getAssessmentId(),
            question.getTitle(),
            question.getMarks(),
            question.getCoId(),
            poIds
        );
    }
}
