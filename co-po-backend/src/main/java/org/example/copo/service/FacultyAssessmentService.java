package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.StudentAssessmentMarks;
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
        questionRepository.deleteById(questionId);
    }

    public List<StudentAssessmentMarks> getMarksForQuestion(Integer questionId) {
        return marksRepository.findByQuestionId(questionId);
    }

    public StudentAssessmentMarks saveStudentMarks(StudentAssessmentMarks marks) {
        return marksRepository.save(marks);
    }
}
