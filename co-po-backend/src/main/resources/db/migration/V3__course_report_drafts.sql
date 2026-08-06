-- Desktop's Course Report screen kept its in-progress draft in a static in-memory
-- HashMap (CourseReportDraftManager), keyed only by course+programme+year - lost on
-- exit, and would let two different faculty on the same course slot (e.g. after a
-- reassignment) silently see each other's free-text draft. This table gives the
-- draft a real home and adds faculty_id to the key so a draft always belongs to
-- exactly one faculty member. The form itself is stored as one JSON blob rather than
-- a column per field - it's a large, self-report-style form with several repeatable
-- table sections, and nothing in it needs to be queried by subfield.
CREATE TABLE IF NOT EXISTS CourseReportDraft (
    id INT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(20) NOT NULL,
    programme VARCHAR(11) NOT NULL,
    academic_year VARCHAR(9) NOT NULL,
    department VARCHAR(3) NOT NULL,
    faculty_id VARCHAR(20) NOT NULL,
    form_json LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uq_course_report_draft (course_code, programme, academic_year, department, faculty_id)
);
