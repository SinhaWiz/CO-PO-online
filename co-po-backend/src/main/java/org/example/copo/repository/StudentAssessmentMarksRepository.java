package org.example.copo.repository;

import org.example.copo.entity.StudentAssessmentMarks;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentAssessmentMarksRepository extends JpaRepository<StudentAssessmentMarks, Integer> {
    List<StudentAssessmentMarks> findByQuestionId(Integer questionId);
    List<StudentAssessmentMarks> findByStudentId(String studentId);
}
