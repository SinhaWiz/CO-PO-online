package org.example.copo.repository;

import org.example.copo.entity.CourseCO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseCORepository extends JpaRepository<CourseCO, CourseCO.CourseCOId> {
    List<CourseCO> findByCourseCodeAndProgramme(String courseCode, String programme);
    void deleteByCourseCodeAndProgramme(String courseCode, String programme);
}
