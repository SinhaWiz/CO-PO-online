package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.exception.ResourceNotFoundException;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacultyAssessmentService {

    private final AssessmentQuestionRepository questionRepository;
    private final StudentAssessmentMarksRepository marksRepository;

    public List<AssessmentQuestion> getQuestionsForAssessment(Integer assessmentId) {
        return questionRepository.findByAssessmentId(assessmentId);
    }

    public AssessmentQuestion createQuestion(AssessmentQuestion question) {
        return questionRepository.save(question);
    }

    public void deleteQuestion(Integer questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question not found: " + questionId);
        }
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
}
