package org.example.copo.service;

import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.Course;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.EnrollmentAttendance;
import org.example.copo.entity.Student;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.CourseRepository;
import org.example.copo.repository.EnrollmentAttendanceRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.example.copo.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ported from the desktop app's ResultService - the weighted final-grade engine every
 * faculty member's View Results screen and admin cohort reports ultimately rely on.
 * Nothing in this environment ever had a live database to run this against for real
 * (noted repeatedly across the phase 5 commits that ported it), so these tests are the
 * first actual verification this logic has had since porting - constructed against a
 * hand-computed expected weighted score rather than just checking "it doesn't throw."
 */
@ExtendWith(MockitoExtension.class)
class CourseResultServiceTest {

    @Mock private CourseAssessmentSectionRepository sectionRepository;
    @Mock private AssessmentRepository assessmentRepository;
    @Mock private AssessmentQuestionRepository questionRepository;
    @Mock private StudentAssessmentMarksRepository marksRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentAttendanceRepository attendanceRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private AssignmentAuthorizationService authorizationService;

    private CourseResultService service;

    private static final String COURSE_CODE = "CSE4101"; // odd last digit -> theory course
    private static final String PROGRAMME = "BSc in SWE";
    private static final String ACADEMIC_YEAR = "2023-2024";
    private static final String DEPARTMENT = "CSE";
    private static final String FACULTY_EMAIL = "faculty@example.com";

    @BeforeEach
    void setUp() {
        service = new CourseResultService(
            sectionRepository, assessmentRepository, questionRepository, marksRepository,
            enrollmentRepository, attendanceRepository, studentRepository, courseRepository, authorizationService
        );
    }

    // ---------------------------------------------------------------------
    // Static helpers - pure functions, no mocking needed
    // ---------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "CSE4101, true", "CSE4103, true", "CSE4111, true",
        "CSE4102, false", "CSE4110, false",
        "CSE410A, false", // non-digit last char
        ", false",
        "'', false",
    })
    void isTheoryCourse_oddLastDigitOnly(String courseCode, boolean expected) {
        assertThat(CourseResultService.isTheoryCourse(courseCode)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "100.0, 10.0", "95.0, 10.0",
        "94.999, 8.0", "90.0, 8.0",
        "89.999, 6.0", "85.0, 6.0",
        "84.999, 4.0", "80.0, 4.0",
        "79.999, 2.0", "75.0, 2.0",
        "74.999, 0.0", "0.0, 0.0",
    })
    void calculateAttendanceMultiplier_matchesCreditBuckets(double attendancePct, double expectedMultiplier) {
        assertThat(CourseResultService.calculateAttendanceMultiplier(attendancePct)).isEqualTo(expectedMultiplier);
    }

    @Test
    void calculateAttendanceRawMarks_scalesMultiplierByCredits() {
        // 92% attendance -> multiplier 8 (>=90, <95); 3 credit course -> 24 raw marks.
        assertThat(CourseResultService.calculateAttendanceRawMarks(92.0, 3.0)).isEqualTo(24.0);
    }

    @Test
    void calculateAttendanceRawMarks_zeroOrNegativeCredits_isZero() {
        assertThat(CourseResultService.calculateAttendanceRawMarks(100.0, 0.0)).isEqualTo(0.0);
        assertThat(CourseResultService.calculateAttendanceRawMarks(100.0, -1.0)).isEqualTo(0.0);
    }

    @Test
    void normalizeAttendanceContribution_dividesBackByCredits() {
        assertThat(CourseResultService.normalizeAttendanceContribution(24.0, 3.0)).isEqualTo(8.0);
        assertThat(CourseResultService.normalizeAttendanceContribution(24.0, 0.0)).isEqualTo(0.0);
    }

    // ---------------------------------------------------------------------
    // getResults() - gating checks
    // ---------------------------------------------------------------------

    @Test
    void getResults_nonTheoryCourse_isBlocked() {
        CourseResultService.ResultsOutcome outcome =
            service.getResults(FACULTY_EMAIL, "CSE4102", PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(outcome.data()).isNull();
        assertThat(outcome.issue()).contains("theory courses");
    }

    @Test
    void getResults_noEnrollments_returnsEmptyResultNotBlocked() {
        when(enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR))
            .thenReturn(List.of());

        CourseResultService.ResultsOutcome outcome =
            service.getResults(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        // Zero enrolled students is a real (if unusual) state, not a validation failure -
        // matches the asymmetry against the CO/PO attainment endpoints, which do block on it.
        assertThat(outcome.issue()).isNull();
        assertThat(outcome.data()).isNotNull();
        assertThat(outcome.data().results()).isEmpty();
        assertThat(outcome.data().statistics().totalStudents()).isZero();
    }

    @Test
    void getResults_below23BatchMissingAttendance_isBlocked() {
        String studentId = "S001";
        stubSingleStudentEnrollment(studentId, 22);
        stubCourseCredits(3.0);
        when(sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(COURSE_CODE, PROGRAMME))
            .thenReturn(List.of());
        when(attendanceRepository.findByCourseIdAndProgrammeAndAcademicYear(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR))
            .thenReturn(List.of()); // nobody's attendance recorded

        CourseResultService.ResultsOutcome outcome =
            service.getResults(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(outcome.data()).isNull();
        assertThat(outcome.issue()).contains("Attendance");
    }

    // ---------------------------------------------------------------------
    // getResults() - weighted math, batch >= 23 (no attendance component)
    // ---------------------------------------------------------------------

    @Test
    void getResults_batch23OrAbove_weightsQuizMidFinalWithoutAttendance() {
        String studentId = "S001";
        stubSingleStudentEnrollment(studentId, 24);
        stubCourseCredits(3.0);
        stubThreeSections(studentId,
            /* quiz obtained/max */ 8.0, 10.0,
            /* mid obtained/max */ 21.0, 30.0,
            /* final obtained/max */ 48.0, 60.0
        );

        CourseResultService.ResultsOutcome outcome =
            service.getResults(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(outcome.issue()).isNull();
        CourseResultService.StudentResultRow row = outcome.data().results().get(0);

        // quiz 80% * 0.20 = 16.0, mid 70% * 0.40 = 28.0, final 80% * 0.40 = 32.0
        assertThat(row.attendanceWeighted()).isEqualTo(0.0);
        assertThat(row.quizAssignmentWeighted()).isCloseTo(16.0, offset(0.001));
        assertThat(row.midWeighted()).isCloseTo(28.0, offset(0.001));
        assertThat(row.finalWeighted()).isCloseTo(32.0, offset(0.001));
        assertThat(row.totalPercentage()).isCloseTo(76.0, offset(0.001));
        assertThat(row.letterGrade()).isEqualTo("A");
        assertThat(row.passed()).isTrue();
        assertThat(outcome.data().batchBelow23()).isFalse();
    }

    // ---------------------------------------------------------------------
    // getResults() - weighted math, batch < 23 (attendance-weighted)
    // ---------------------------------------------------------------------

    @Test
    void getResults_belowBatch23_weightsAttendanceQuizMidFinal() {
        String studentId = "S001";
        stubSingleStudentEnrollment(studentId, 22);
        stubCourseCredits(3.0);
        stubThreeSections(studentId, 8.0, 10.0, 21.0, 30.0, 48.0, 60.0);
        when(attendanceRepository.findByCourseIdAndProgrammeAndAcademicYear(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR))
            .thenReturn(List.of(attendanceRow(studentId, 92.0)));

        CourseResultService.ResultsOutcome outcome =
            service.getResults(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(outcome.issue()).isNull();
        CourseResultService.StudentResultRow row = outcome.data().results().get(0);

        // 92% attendance -> multiplier 8 -> raw 24 marks / 3 credits = 8.0 contribution
        // quiz 80%*0.15=12.0, mid 70%*0.25=17.5, final 80%*0.50=40.0
        assertThat(row.attendanceWeighted()).isCloseTo(8.0, offset(0.001));
        assertThat(row.quizAssignmentWeighted()).isCloseTo(12.0, offset(0.001));
        assertThat(row.midWeighted()).isCloseTo(17.5, offset(0.001));
        assertThat(row.finalWeighted()).isCloseTo(40.0, offset(0.001));
        assertThat(row.totalPercentage()).isCloseTo(77.5, offset(0.001));
        assertThat(row.letterGrade()).isEqualTo("A");
        assertThat(outcome.data().batchBelow23()).isTrue();
        assertThat(outcome.data().majorityBatch()).isEqualTo(22);
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private void stubSingleStudentEnrollment(String studentId, int batch) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(COURSE_CODE);
        enrollment.setProgramme(PROGRAMME);
        enrollment.setAcademicYear(ACADEMIC_YEAR);
        when(enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR))
            .thenReturn(List.of(enrollment));

        Student student = new Student();
        student.setId(studentId);
        student.setName("Test Student");
        student.setBatch(batch);
        student.setDepartment(DEPARTMENT);
        student.setProgramme(PROGRAMME);
        when(studentRepository.findAllById(anyList())).thenReturn(List.of(student));
    }

    private void stubCourseCredits(double credits) {
        Course course = new Course();
        course.setCourseCode(COURSE_CODE);
        course.setProgramme(PROGRAMME);
        course.setCredits(credits);
        course.setDepartment(DEPARTMENT);
        course.setCourseName("Test Course");
        when(courseRepository.findById(new Course.CourseId(COURSE_CODE, PROGRAMME))).thenReturn(Optional.of(course));
    }

    // Sets up one Quiz, one Mid, one Final section, each with a single question, and
    // marks such that section percentage = obtained/max*100 comes out exactly as given.
    private void stubThreeSections(
        String studentId,
        double quizObtained, double quizMax,
        double midObtained, double midMax,
        double finalObtained, double finalMax
    ) {
        CourseAssessmentSection quiz = section(1, "Quiz 1", 1);
        CourseAssessmentSection mid = section(2, "Mid", 2);
        CourseAssessmentSection fin = section(3, "Final", 3);
        when(sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(COURSE_CODE, PROGRAMME))
            .thenReturn(List.of(quiz, mid, fin));

        stubSectionMarks(quiz, 101, studentId, quizObtained, quizMax);
        stubSectionMarks(mid, 102, studentId, midObtained, midMax);
        stubSectionMarks(fin, 103, studentId, finalObtained, finalMax);
    }

    private void stubSectionMarks(CourseAssessmentSection section, int questionId, String studentId, double obtained, double max) {
        Assessment assessment = new Assessment();
        assessment.setId(section.getId());
        assessment.setSectionId(section.getId());
        assessment.setAcademicYear(ACADEMIC_YEAR);
        when(assessmentRepository.findBySectionIdAndAcademicYear(eq(section.getId()), eq(ACADEMIC_YEAR)))
            .thenReturn(Optional.of(assessment));

        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(questionId);
        question.setAssessmentId(assessment.getId());
        question.setTitle("Q1");
        question.setMarks(max);
        when(questionRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of(question));

        StudentAssessmentMarks mark = new StudentAssessmentMarks();
        mark.setId(questionId);
        mark.setStudentId(studentId);
        mark.setQuestionId(questionId);
        mark.setMarksObtained(obtained);
        when(marksRepository.findByQuestionIdIn(List.of(questionId))).thenReturn(List.of(mark));
    }

    private CourseAssessmentSection section(int id, String displayName, int order) {
        CourseAssessmentSection section = new CourseAssessmentSection();
        section.setId(id);
        section.setCourseCode(COURSE_CODE);
        section.setProgramme(PROGRAMME);
        section.setDisplayName(displayName);
        section.setSectionOrder(order);
        return section;
    }

    private EnrollmentAttendance attendanceRow(String studentId, double pct) {
        EnrollmentAttendance attendance = new EnrollmentAttendance();
        attendance.setStudentId(studentId);
        attendance.setCourseId(COURSE_CODE);
        attendance.setProgramme(PROGRAMME);
        attendance.setAcademicYear(ACADEMIC_YEAR);
        attendance.setAttendancePercentage(pct);
        return attendance;
    }
}
