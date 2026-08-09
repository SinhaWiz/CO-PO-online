-- Baseline schema for co-po-backend.
--
-- Adapted from the legacy JavaFX app's src/main/resources/schema.sql so this backend
-- can stand up its own database instead of assuming that schema was already applied
-- by hand. One deliberate deviation from the legacy schema: AssessmentQuestion below
-- carries a `po_id` column that the legacy schema does not define (there, a question's
-- POs live only in AssessmentQuestion_PO, a proper many-to-many table). The current
-- backend code (AssessmentQuestion entity, AdminReportService's cohort PO report SQL)
-- was written against a single po_id column, so this migration matches that reality to
-- keep today's functionality working. AssessmentQuestion_PO is still created here too -
-- restoring real many-to-many PO mapping (and retiring po_id) is planned separately.

CREATE TABLE Admin (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    is_super_admin BOOLEAN DEFAULT FALSE,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE Faculty (
    id VARCHAR(20) PRIMARY KEY,
    shortname VARCHAR(50) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE Course (
    course_code VARCHAR(20),
    course_name VARCHAR(100) NOT NULL,
    credits DECIMAL(3,2) NOT NULL,
    department VARCHAR(3) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    PRIMARY KEY (course_code, programme)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Per-course assessment section configuration. Admins can add any number of sections
-- with any names - not tied to fixed QUIZ1-4/MID/FINAL slots.
CREATE TABLE CourseAssessmentSection (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    section_order INT NOT NULL,
    FOREIGN KEY (course_code, programme) REFERENCES Course(course_code, programme),
    UNIQUE (course_code, programme, display_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE CO (
    id INT AUTO_INCREMENT PRIMARY KEY,
    co_number VARCHAR(10) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE PO (
    id INT AUTO_INCREMENT PRIMARY KEY,
    po_number VARCHAR(10) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE CourseAssignment (
    id INT AUTO_INCREMENT PRIMARY KEY,
    faculty_id VARCHAR(20) NOT NULL,
    course_code VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    academic_year VARCHAR(9) NOT NULL,
    department VARCHAR(3) NOT NULL,
    FOREIGN KEY (faculty_id) REFERENCES Faculty(id),
    FOREIGN KEY (course_code, programme) REFERENCES Course(course_code, programme),
    UNIQUE (course_code, programme, academic_year, department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE CulminationCourse (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(20),
    programme VARCHAR(11),
    FOREIGN KEY (course_code, programme) REFERENCES Course(course_code, programme)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE Course_CO (
    course_code VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    co_id INT NOT NULL,
    PRIMARY KEY (course_code, programme, co_id),
    FOREIGN KEY (course_code, programme) REFERENCES Course(course_code, programme),
    FOREIGN KEY (co_id) REFERENCES CO(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE Course_PO (
    course_code VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    po_id INT NOT NULL,
    PRIMARY KEY (course_code, programme, po_id),
    FOREIGN KEY (course_code, programme) REFERENCES Course(course_code, programme),
    FOREIGN KEY (po_id) REFERENCES PO(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE Student (
    id VARCHAR(9) PRIMARY KEY,
    batch INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    department VARCHAR(3),
    programme VARCHAR(11)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE GraduatingStudent (
    id VARCHAR(9) PRIMARY KEY,
    FOREIGN KEY (id) REFERENCES Student(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE NonGraduatingStudentComment (
    student_id VARCHAR(9) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    batch INT NOT NULL,
    comment TEXT,
    PRIMARY KEY (student_id, programme, batch),
    FOREIGN KEY (student_id) REFERENCES Student(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE Enrollment (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(9) NOT NULL,
    course_id VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    academic_year VARCHAR(9) NOT NULL,
    FOREIGN KEY (student_id) REFERENCES Student(id),
    FOREIGN KEY (course_id, programme) REFERENCES Course(course_code, programme),
    UNIQUE (student_id, course_id, programme, academic_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE EnrollmentAttendance (
    student_id VARCHAR(9) NOT NULL,
    course_id VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    academic_year VARCHAR(9) NOT NULL,
    attendance_percentage DECIMAL(5,2) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, course_id, programme, academic_year),
    FOREIGN KEY (student_id, course_id, programme, academic_year)
        REFERENCES Enrollment(student_id, course_id, programme, academic_year)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Global fallback thresholds. Primary-keyed on `type` (the legacy desktop schema left
-- this table keyless; the backend's admin-configuration service has always relied on
-- `type` being unique for its upsert logic, so make that explicit here).
CREATE TABLE Thresholds (
    type VARCHAR(25) PRIMARY KEY,
    percentage DECIMAL(4,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Per-course-assignment threshold overrides. Admins set initial values when assigning
-- a course; faculty can update thresholds later for their own assignments.
CREATE TABLE CourseAssignmentThreshold (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    academic_year VARCHAR(9) NOT NULL,
    department VARCHAR(3) NOT NULL,
    co_individual DECIMAL(4,2) DEFAULT 60.00,
    po_individual DECIMAL(4,2) DEFAULT 40.00,
    co_cohort DECIMAL(4,2) DEFAULT 50.00,
    po_cohort DECIMAL(4,2) DEFAULT 50.00,
    UNIQUE (course_code, programme, academic_year, department),
    FOREIGN KEY (course_code, programme) REFERENCES Course(course_code, programme)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- One "instance" of a section for a specific academic year.
CREATE TABLE Assessment (
    id INT AUTO_INCREMENT PRIMARY KEY,
    section_id INT NOT NULL,
    academic_year VARCHAR(9) NOT NULL,
    total_marks DECIMAL(5,2) DEFAULT 0,
    FOREIGN KEY (section_id) REFERENCES CourseAssessmentSection(id) ON DELETE CASCADE,
    UNIQUE (section_id, academic_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE AssessmentQuestion (
    id INT AUTO_INCREMENT PRIMARY KEY,
    assessment_id INT NOT NULL,
    title VARCHAR(20) NOT NULL,
    marks DECIMAL(5,2) NOT NULL,
    co_id INT NULL,
    po_id INT NULL,
    FOREIGN KEY (assessment_id) REFERENCES Assessment(id) ON DELETE CASCADE,
    FOREIGN KEY (co_id) REFERENCES CO(id),
    FOREIGN KEY (po_id) REFERENCES PO(id),
    UNIQUE (assessment_id, title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Proper many-to-many question<->PO mapping, as the legacy desktop app models it.
-- Not read/written by the backend yet (it currently uses AssessmentQuestion.po_id
-- above instead) - created now so that switch-over doesn't need its own migration.
CREATE TABLE AssessmentQuestion_PO (
    question_id INT NOT NULL,
    po_id INT NOT NULL,
    PRIMARY KEY (question_id, po_id),
    FOREIGN KEY (question_id) REFERENCES AssessmentQuestion(id) ON DELETE CASCADE,
    FOREIGN KEY (po_id) REFERENCES PO(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE StudentAssessmentMarks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(9) NOT NULL,
    question_id INT NOT NULL,
    marks_obtained DECIMAL(5,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (student_id) REFERENCES Student(id),
    FOREIGN KEY (question_id) REFERENCES AssessmentQuestion(id) ON DELETE CASCADE,
    UNIQUE (student_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Performance indexes.
-- NOTE: several obvious ones are skipped because a UNIQUE constraint's leading
-- column already covers them (matches the legacy schema's own notes).
CREATE INDEX idx_assessmentquestion_assessment ON AssessmentQuestion(assessment_id);
CREATE INDEX idx_assessmentquestionpo_po ON AssessmentQuestion_PO(po_id);
CREATE INDEX idx_studentassessmentmarks_question ON StudentAssessmentMarks(question_id);
CREATE INDEX idx_courseassignment_faculty ON CourseAssignment(faculty_id);
CREATE INDEX idx_enrollment_course_year ON Enrollment(course_id, programme, academic_year);
CREATE INDEX idx_student_programme_batch ON Student(programme, batch);
CREATE INDEX idx_course_programme ON Course(programme);
CREATE INDEX idx_courseco_course ON Course_CO(course_code, programme);
CREATE INDEX idx_coursepo_course ON Course_PO(course_code, programme);

-- Unified view for CO/PO analysis (kept for parity with the desktop app; not yet
-- queried by the backend, which still does its own per-report joins).
CREATE VIEW StudentAssessmentPerformance AS
SELECT
    s.id as student_id,
    s.name as student_name,
    c.course_code,
    c.programme,
    c.course_name,
    cas.id as section_id,
    cas.display_name as section_name,
    cas.section_order,
    a.academic_year,
    aq.id as question_id,
    aq.title as question_title,
    aq.marks as max_marks,
    sam.marks_obtained,
    co.co_number,
    po.po_number
FROM Student s
         JOIN Enrollment e ON s.id = e.student_id
         JOIN Course c ON e.course_id = c.course_code AND e.programme = c.programme
         JOIN CourseAssessmentSection cas ON c.course_code = cas.course_code AND c.programme = cas.programme
         JOIN Assessment a ON cas.id = a.section_id AND a.academic_year = e.academic_year
         JOIN AssessmentQuestion aq ON a.id = aq.assessment_id
         LEFT JOIN StudentAssessmentMarks sam ON s.id = sam.student_id AND aq.id = sam.question_id
         LEFT JOIN CO co ON aq.co_id = co.id
         LEFT JOIN AssessmentQuestion_PO aqp ON aq.id = aqp.question_id
         LEFT JOIN PO po ON aqp.po_id = po.id;

-- Seed master CO/PO lists (CO1-CO20, PO1-PO12) and default global thresholds -
-- matches the legacy desktop app's insert.sql / AdminConfigurationService defaults.
INSERT INTO Admin (email, password, is_super_admin, created_by)
VALUES ('admin@iut-dhaka.edu', 'Password123', FALSE, 'system');
INSERT INTO CO (co_number) VALUES
    ('CO1'), ('CO2'), ('CO3'), ('CO4'), ('CO5'), ('CO6'), ('CO7'), ('CO8'), ('CO9'), ('CO10'),
    ('CO11'), ('CO12'), ('CO13'), ('CO14'), ('CO15'), ('CO16'), ('CO17'), ('CO18'), ('CO19'), ('CO20');

INSERT INTO PO (po_number) VALUES
    ('PO1'), ('PO2'), ('PO3'), ('PO4'), ('PO5'), ('PO6'), ('PO7'), ('PO8'), ('PO9'), ('PO10'), ('PO11'), ('PO12');

INSERT INTO Thresholds (type, percentage) VALUES
    ('CO_INDIVIDUAL', 60.00),
    ('PO_INDIVIDUAL', 40.00),
    ('CO_COHORTSET', 50.00),
    ('PO_COHORTSET', 50.00);
