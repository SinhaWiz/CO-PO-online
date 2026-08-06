package org.example.copo.repository;

import org.example.copo.entity.StudentAssessmentMarks;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentAssessmentMarksRepository extends JpaRepository<StudentAssessmentMarks, Integer> {
    List<StudentAssessmentMarks> findByQuestionId(Integer questionId);
    List<StudentAssessmentMarks> findByStudentId(String studentId);
    Optional<StudentAssessmentMarks> findByStudentIdAndQuestionId(String studentId, Integer questionId);
}
