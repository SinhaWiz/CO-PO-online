package org.example.copo.repository;

import org.example.copo.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Course.CourseId> {
    List<Course> findByProgramme(String programme);
    List<Course> findByDepartment(String department);
}