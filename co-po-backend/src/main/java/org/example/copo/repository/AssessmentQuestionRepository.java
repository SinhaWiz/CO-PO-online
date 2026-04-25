package org.example.copo.repository;

import org.example.copo.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Integer> {
    List<AssessmentQuestion> findByAssessmentId(Integer assessmentId);
}
