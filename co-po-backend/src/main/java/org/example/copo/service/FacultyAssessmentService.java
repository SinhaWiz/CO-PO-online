package org.example.copo.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.AssessmentQuestionPO;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.AssessmentQuestionPORepository;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
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
    private final StudentAssessmentMarksRepository marksRepository;

    public record QuestionDto(Integer id, Integer assessmentId, String title, Double marks, Integer coId, List<Integer> poIds) {}

    public record CreateQuestionRequest(
        @NotNull Integer assessmentId,
        @NotBlank String title,
        @NotNull @Positive Double marks,
        Integer coId,
        List<Integer> poIds
    ) {}

    public List<QuestionDto> getQuestionsForAssessment(Integer assessmentId) {
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
    public QuestionDto createQuestion(CreateQuestionRequest request) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setAssessmentId(request.assessmentId());
        question.setTitle(request.title());
        question.setMarks(request.marks());
        question.setCoId(request.coId());
        AssessmentQuestion saved = questionRepository.save(question);

        List<Integer> poIds = request.poIds() == null ? List.of() : request.poIds().stream().distinct().toList();
        for (Integer poId : poIds) {
            questionPoRepository.save(new AssessmentQuestionPO(saved.getId(), poId));
        }

        return toDto(saved, poIds);
    }

    public void deleteQuestion(Integer questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question not found: " + questionId);
        }
        // AssessmentQuestion_PO rows are ON DELETE CASCADE, no separate cleanup needed.
        questionRepository.deleteById(questionId);
    }

    public List<StudentAssessmentMarks> getMarksForQuestion(Integer questionId) {
        return marksRepository.findByQuestionId(questionId);
    }

    // Upserts by (studentId, questionId) instead of blindly saving the client-supplied
    // entity. Marks carry no natural unique id from the client, so a plain save() would
    // either duplicate the row or fail a unique-constraint check on every re-grade.
    public StudentAssessmentMarks saveStudentMarks(StudentAssessmentMarks marks) {
        StudentAssessmentMarks existing = marksRepository
            .findByStudentIdAndQuestionId(marks.getStudentId(), marks.getQuestionId())
            .orElse(null);
        if (existing != null) {
            existing.setMarksObtained(marks.getMarksObtained());
            return marksRepository.save(existing);
        }
        return marksRepository.save(marks);
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
