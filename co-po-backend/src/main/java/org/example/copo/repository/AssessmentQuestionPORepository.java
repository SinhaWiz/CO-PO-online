package org.example.copo.repository;

import org.example.copo.entity.AssessmentQuestionPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentQuestionPORepository extends JpaRepository<AssessmentQuestionPO, AssessmentQuestionPO.AssessmentQuestionPOId> {
    List<AssessmentQuestionPO> findByQuestionId(Integer questionId);
    List<AssessmentQuestionPO> findByQuestionIdIn(List<Integer> questionIds);
    void deleteByQuestionId(Integer questionId);
}
