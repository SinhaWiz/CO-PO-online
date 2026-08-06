package org.example.copo.repository;

import org.example.copo.entity.CoursePO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoursePORepository extends JpaRepository<CoursePO, CoursePO.CoursePOId> {
    List<CoursePO> findByCourseCodeAndProgramme(String courseCode, String programme);
    void deleteByCourseCodeAndProgramme(String courseCode, String programme);
    boolean existsByCourseCodeAndProgrammeAndPoId(String courseCode, String programme, Integer poId);
}
