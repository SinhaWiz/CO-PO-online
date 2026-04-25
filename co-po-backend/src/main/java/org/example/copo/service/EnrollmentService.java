package org.example.copo.service;

import lombok.RequiredArgsConstructor;
import org.example.copo.entity.Enrollment;
import org.example.copo.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;

    public List<Enrollment> getAllEnrollments() {
        return enrollmentRepository.findAll();
    }

    public Enrollment createEnrollment(Enrollment enrollment) {
        return enrollmentRepository.save(enrollment);
    }

    public void deleteEnrollment(String studentId, String courseId, String programme, String academicYear) {
        Enrollment.EnrollmentId id = new Enrollment.EnrollmentId(studentId, courseId, programme, academicYear);
        enrollmentRepository.deleteById(id);
    }
}
