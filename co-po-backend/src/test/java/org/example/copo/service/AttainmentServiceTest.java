package org.example.copo.service;

import org.example.copo.entity.Assessment;
import org.example.copo.entity.AssessmentQuestion;
import org.example.copo.entity.AssessmentQuestionPO;
import org.example.copo.entity.CO;
import org.example.copo.entity.CourseAssessmentSection;
import org.example.copo.entity.Enrollment;
import org.example.copo.entity.PO;
import org.example.copo.entity.StudentAssessmentMarks;
import org.example.copo.repository.AssessmentQuestionPORepository;
import org.example.copo.repository.AssessmentQuestionRepository;
import org.example.copo.repository.AssessmentRepository;
import org.example.copo.repository.CORepository;
import org.example.copo.repository.CourseAssessmentSectionRepository;
import org.example.copo.repository.EnrollmentRepository;
import org.example.copo.repository.PORepository;
import org.example.copo.repository.StudentAssessmentMarksRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CO/PO attainment is the core accreditation number this whole app exists to produce -
 * ported directly from the desktop app's COReportDialogController/POReportDialogController
 * with the gating rules (every relevant cell must be graded before a number is
 * published) kept intentionally strict. Like CourseResultServiceTest, this is the
 * first real verification this logic has had - no live database was available at any
 * point in this migration to run it against for real.
 *
 * The PO test suite specifically pins down a counting convention that was originally
 * ported incorrectly (fixed in phase 5.4, see AttainmentService.buildPoMatrix's own
 * comment): totalRequired/graded count once per (student, question), not once per
 * (student, question, PO) - a question mapped to two POs is still one mark cell to
 * grade, not two. Getting this wrong doesn't change the gating boolean in the simple
 * case, but it does produce a false "not fully graded" block once a question maps to
 * more than one PO, which is exactly what poAttainment_multiPoQuestion_countsOnce below
 * would have caught.
 */
@ExtendWith(MockitoExtension.class)
class AttainmentServiceTest {

    @Mock private CourseAssessmentSectionRepository sectionRepository;
    @Mock private AssessmentRepository assessmentRepository;
    @Mock private AssessmentQuestionRepository questionRepository;
    @Mock private AssessmentQuestionPORepository questionPoRepository;
    @Mock private StudentAssessmentMarksRepository marksRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CORepository coRepository;
    @Mock private PORepository poRepository;
    @Mock private CourseAssignmentThresholdService thresholdService;
    @Mock private AssignmentAuthorizationService authorizationService;

    private AttainmentService service;

    private static final String COURSE_CODE = "CSE4101";
    private static final String PROGRAMME = "BSc in SWE";
    private static final String ACADEMIC_YEAR = "2023-2024";
    private static final String DEPARTMENT = "CSE";
    private static final String FACULTY_EMAIL = "faculty@example.com";
    private static final int SECTION_ID = 1;

    @BeforeEach
    void setUp() {
        service = new AttainmentService(
            sectionRepository, assessmentRepository, questionRepository, questionPoRepository,
            marksRepository, enrollmentRepository, coRepository, poRepository, thresholdService, authorizationService
        );
        lenient().when(coRepository.findAll()).thenReturn(List.of(new CO(1, "CO1")));
        lenient().when(poRepository.findAll()).thenReturn(List.of(new PO(1, "PO1"), new PO(2, "PO2")));
    }

    // ---------------------------------------------------------------------
    // CO attainment - gating
    // ---------------------------------------------------------------------

    @Test
    void coAttainment_noSections_isBlocked() {
        when(sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(COURSE_CODE, PROGRAMME))
            .thenReturn(List.of());

        AttainmentService.AttainmentResult result =
            service.getCoAttainment(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(result.rows()).isEmpty();
        assertThat(result.issues()).containsExactly("No assessment sections configured for this course.");
    }

    @Test
    void coAttainment_sectionWithNoCoMappedQuestions_isBlocked() {
        CourseAssessmentSection section = section();
        when(sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(COURSE_CODE, PROGRAMME))
            .thenReturn(List.of(section));
        Assessment assessment = assessment();
        when(assessmentRepository.findBySectionIdAndAcademicYear(SECTION_ID, ACADEMIC_YEAR)).thenReturn(Optional.of(assessment));

        AssessmentQuestion unmappedQuestion = question(101, null, 10.0);
        when(questionRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of(unmappedQuestion));

        AttainmentService.AttainmentResult result =
            service.getCoAttainment(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(result.rows()).isEmpty();
        assertThat(result.issues()).anyMatch(issue -> issue.contains("none mapped to a CO"));
    }

    @Test
    void coAttainment_noStudentsEnrolled_isBlocked() {
        stubOneCoMappedQuestion(101, 10.0);
        when(enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR))
            .thenReturn(List.of());

        AttainmentService.AttainmentResult result =
            service.getCoAttainment(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(result.issues()).containsExactly("No students enrolled for this course in " + ACADEMIC_YEAR + ".");
    }

    @Test
    void coAttainment_partiallyGraded_isBlockedWithCount() {
        stubOneCoMappedQuestion(101, 10.0);
        stubEnrollment("S001", "S002");
        // Only one of two students graded.
        when(marksRepository.findByQuestionIdIn(List.of(101)))
            .thenReturn(List.of(mark("S001", 101, 8.0)));

        AttainmentService.AttainmentResult result =
            service.getCoAttainment(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(result.issues()).containsExactly("Not all required marks are graded yet (1 of 2).");
    }

    @Test
    void coAttainment_fullyGraded_computesAttainmentAgainstIndividualThreshold() {
        stubOneCoMappedQuestion(101, 10.0);
        stubEnrollment("S001", "S002");
        when(marksRepository.findByQuestionIdIn(List.of(101))).thenReturn(List.of(
            mark("S001", 101, 8.0),  // 80% -> attains at a 60% threshold
            mark("S002", 101, 5.0)   // 50% -> does not attain
        ));
        when(thresholdService.getThresholds(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT))
            .thenReturn(new CourseAssignmentThresholdService.ThresholdsDto(60.0, 40.0, 50.0, 50.0));

        AttainmentService.AttainmentResult result =
            service.getCoAttainment(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(result.issues()).isEmpty();
        assertThat(result.rows()).hasSize(1);
        AttainmentService.OutcomeAttainmentRow row = result.rows().get(0);
        assertThat(row.code()).isEqualTo("CO1");
        // 1 of 2 students attained (S001 only) -> 50%.
        assertThat(row.attainedPercent()).isCloseTo(50.0, offset(0.001));
    }

    // ---------------------------------------------------------------------
    // PO attainment - the multi-PO counting convention
    // ---------------------------------------------------------------------

    @Test
    void poAttainment_multiPoQuestion_countsGradedOncePerQuestionNotPerPo() {
        CourseAssessmentSection section = section();
        when(sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(COURSE_CODE, PROGRAMME))
            .thenReturn(List.of(section));
        Assessment assessment = assessment();
        when(assessmentRepository.findBySectionIdAndAcademicYear(SECTION_ID, ACADEMIC_YEAR)).thenReturn(Optional.of(assessment));

        AssessmentQuestion question = question(101, null, 10.0);
        when(questionRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of(question));
        // One question mapped to TWO POs.
        when(questionPoRepository.findByQuestionIdIn(List.of(101))).thenReturn(List.of(
            new AssessmentQuestionPO(101, 1),
            new AssessmentQuestionPO(101, 2)
        ));

        stubEnrollment("S001");
        // Exactly one mark recorded for the one question - if graded/totalRequired were
        // (incorrectly) counted per (student, question, PO) this would read as 1 of 2
        // and wrongly block; counted per (student, question) it's 1 of 1 and passes.
        when(marksRepository.findByQuestionIdIn(List.of(101))).thenReturn(List.of(mark("S001", 101, 10.0)));
        when(thresholdService.getThresholds(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT))
            .thenReturn(new CourseAssignmentThresholdService.ThresholdsDto(60.0, 40.0, 50.0, 50.0));

        AttainmentService.AttainmentResult result =
            service.getPoAttainment(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(result.issues()).isEmpty();
        // Full marks on the one question -> both POs it maps to are fully attained by the one student.
        assertThat(result.rows()).extracting(AttainmentService.OutcomeAttainmentRow::code).containsExactlyInAnyOrder("PO1", "PO2");
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.attainedPercent()).isCloseTo(100.0, offset(0.001)));
    }

    @Test
    void poAttainment_questionWithNoPoMapping_isBlocked() {
        CourseAssessmentSection section = section();
        when(sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(COURSE_CODE, PROGRAMME))
            .thenReturn(List.of(section));
        Assessment assessment = assessment();
        when(assessmentRepository.findBySectionIdAndAcademicYear(SECTION_ID, ACADEMIC_YEAR)).thenReturn(Optional.of(assessment));
        AssessmentQuestion question = question(101, null, 10.0);
        when(questionRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of(question));
        when(questionPoRepository.findByQuestionIdIn(List.of(101))).thenReturn(List.of());

        AttainmentService.AttainmentResult result =
            service.getPoAttainment(FACULTY_EMAIL, COURSE_CODE, PROGRAMME, ACADEMIC_YEAR, DEPARTMENT);

        assertThat(result.issues()).anyMatch(issue -> issue.contains("none mapped to a PO"));
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private void stubOneCoMappedQuestion(int questionId, double maxMarks) {
        CourseAssessmentSection section = section();
        when(sectionRepository.findByCourseCodeAndProgrammeOrderBySectionOrderAsc(COURSE_CODE, PROGRAMME))
            .thenReturn(List.of(section));
        Assessment assessment = assessment();
        when(assessmentRepository.findBySectionIdAndAcademicYear(SECTION_ID, ACADEMIC_YEAR)).thenReturn(Optional.of(assessment));
        AssessmentQuestion coMappedQuestion = question(questionId, 1, maxMarks);
        when(questionRepository.findByAssessmentId(assessment.getId())).thenReturn(List.of(coMappedQuestion));
    }

    private void stubEnrollment(String... studentIds) {
        List<Enrollment> enrollments = List.of(studentIds).stream().map(id -> {
            Enrollment enrollment = new Enrollment();
            enrollment.setStudentId(id);
            enrollment.setCourseId(COURSE_CODE);
            enrollment.setProgramme(PROGRAMME);
            enrollment.setAcademicYear(ACADEMIC_YEAR);
            return enrollment;
        }).toList();
        when(enrollmentRepository.findByCourseIdAndProgrammeAndAcademicYear(COURSE_CODE, PROGRAMME, ACADEMIC_YEAR))
            .thenReturn(enrollments);
    }

    private CourseAssessmentSection section() {
        CourseAssessmentSection section = new CourseAssessmentSection();
        section.setId(SECTION_ID);
        section.setCourseCode(COURSE_CODE);
        section.setProgramme(PROGRAMME);
        section.setDisplayName("Quiz 1");
        section.setSectionOrder(1);
        return section;
    }

    private Assessment assessment() {
        Assessment assessment = new Assessment();
        assessment.setId(SECTION_ID);
        assessment.setSectionId(SECTION_ID);
        assessment.setAcademicYear(ACADEMIC_YEAR);
        return assessment;
    }

    private AssessmentQuestion question(int id, Integer coId, double marks) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(id);
        question.setAssessmentId(SECTION_ID);
        question.setTitle("Q1");
        question.setMarks(marks);
        question.setCoId(coId);
        return question;
    }

    private StudentAssessmentMarks mark(String studentId, int questionId, double obtained) {
        StudentAssessmentMarks mark = new StudentAssessmentMarks();
        mark.setStudentId(studentId);
        mark.setQuestionId(questionId);
        mark.setMarksObtained(obtained);
        return mark;
    }
}
