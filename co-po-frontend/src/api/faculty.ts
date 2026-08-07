import api from './axios';

export interface AssessmentQuestion {
  id?: number;
  assessmentId: number;
  title: string;
  marks: number;
  coId?: number;
  poIds?: number[];
}

export interface StudentAssessmentMarks {
  id?: number;
  studentId: string;
  questionId: number;
  marksObtained: number;
}

// Fetch Questions for an Assessment
export const getQuestions = (assessmentId: number) => 
  api.get<AssessmentQuestion[]>(`/faculty/assessments/questions/${assessmentId}`);

// Create a Question
export const createQuestion = (data: AssessmentQuestion) =>
  api.post<AssessmentQuestion>('/faculty/assessments/questions', data);

// Update a Question (assessmentId can't change - a question belongs to a fixed assessment)
export const updateQuestion = (questionId: number, data: Omit<AssessmentQuestion, 'id' | 'assessmentId'>) =>
  api.put<AssessmentQuestion>(`/faculty/assessments/questions/${questionId}`, data);

// Delete a Question
export const deleteQuestion = (questionId: number) =>
  api.delete(`/faculty/assessments/questions/${questionId}`);

// Fetch Marks for a Question
export const getMarks = (questionId: number) => 
  api.get<StudentAssessmentMarks[]>(`/faculty/assessments/marks/question/${questionId}`);

// Save/Update Student Mark
export const saveMarks = (data: StudentAssessmentMarks) =>
  api.post<StudentAssessmentMarks>('/faculty/assessments/marks', data);

// Change own password
export const changeFacultyPassword = (currentPassword: string, newPassword: string, confirmPassword: string) =>
  api.post<{ message: string }>('/faculty/account/change-password', {
    currentPassword,
    newPassword,
    confirmPassword,
  });

export interface CourseSection {
  id: number;
  courseCode: string;
  programme: string;
  displayName: string;
  sectionOrder: number;
}

export interface AssessmentInstance {
  id: number;
  sectionId: number;
  academicYear: string;
  totalMarks: number;
}

// Read-only: which sections a course has (admin-defined, faculty can't edit them here)
export const getSectionsForCourse = (courseCode: string, programme: string) =>
  api.get<CourseSection[]>(`/faculty/assessments/sections/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}`);

// Resolves (section, year) to a real Assessment id, creating it the first time
export const resolveAssessmentInstance = (sectionId: number, academicYear: string) =>
  api.post<AssessmentInstance>('/faculty/assessments/instances', { sectionId, academicYear });

export interface MyAssignment {
  courseCode: string;
  programme: string;
  courseName: string;
  academicYear: string;
  department: string;
}

export interface CourseOutcomeIds {
  coIds: number[];
  poIds: number[];
}

// The faculty member's own course assignments - used to build pickers without
// requiring anyone to type in a course code or academic year by hand.
export const getMyAssignments = () => api.get<MyAssignment[]>('/faculty/assignments/mine');

// CO/PO master lists and a course's allow-list, mirroring the admin-only endpoints
// under /api/admin/outcomes/** and /api/admin/courses/**/outcomes - those paths are
// locked to ADMIN at the security filter level, so faculty need their own copies.
export const getFacultyCOs = () => api.get<{ id: number; coNumber: string }[]>('/faculty/assessments/outcomes/co');
export const getFacultyPOs = () => api.get<{ id: number; poNumber: string }[]>('/faculty/assessments/outcomes/po');
export const getFacultyCourseOutcomes = (courseCode: string, programme: string) =>
  api.get<CourseOutcomeIds>(`/faculty/assessments/outcomes/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}`);

export interface Thresholds {
  coIndividual: number;
  poIndividual: number;
  coCohort: number;
  poCohort: number;
}

const facultyAssignmentThresholdsUrl = (courseCode: string, programme: string, academicYear: string, department: string) =>
  `/faculty/assignments/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}/thresholds`;

export const getMyAssignmentThresholds = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.get<Thresholds>(facultyAssignmentThresholdsUrl(courseCode, programme, academicYear, department));
export const updateMyAssignmentThresholds = (
  courseCode: string, programme: string, academicYear: string, department: string, data: Thresholds,
) => api.put<Thresholds>(facultyAssignmentThresholdsUrl(courseCode, programme, academicYear, department), data);

export interface OutcomeAttainmentRow {
  code: string;
  attainedPercent: number;
}

export interface AttainmentResult {
  rows: OutcomeAttainmentRow[];
  issues: string[];
}

const attainmentUrl = (kind: 'co' | 'po', courseCode: string, programme: string, academicYear: string, department: string) =>
  `/faculty/attainment/${kind}/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`;

// issues is non-empty (and rows empty) whenever grading isn't 100% complete or some
// section is missing CO/PO mapping - the backend refuses to publish a partial number.
export const getCoAttainment = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.get<AttainmentResult>(attainmentUrl('co', courseCode, programme, academicYear, department));
export const getPoAttainment = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.get<AttainmentResult>(attainmentUrl('po', courseCode, programme, academicYear, department));

export interface ReportComment {
  code: string;
  comment: string;
  suggestions: string;
}

export interface GeneratedReport {
  pdfFileName: string | null;
  rows: OutcomeAttainmentRow[];
  issues: string[];
}

const outcomeReportUrl = (kind: 'co' | 'po', courseCode: string, programme: string, academicYear: string, department: string) =>
  `/faculty/reports/${kind}/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`;

export const generateCoReport = (
  courseCode: string, programme: string, academicYear: string, department: string, comments: ReportComment[],
) => api.post<GeneratedReport>(outcomeReportUrl('co', courseCode, programme, academicYear, department), { comments });
export const generatePoReport = (
  courseCode: string, programme: string, academicYear: string, department: string, comments: ReportComment[],
) => api.post<GeneratedReport>(outcomeReportUrl('po', courseCode, programme, academicYear, department), { comments });

// Faculty-scoped mirror of downloadAdminReport (api/reports.ts) - same backend file
// resolver, reachable with a FACULTY-role JWT instead of an ADMIN one.
export const downloadFacultyReport = async (type: string, name: string) => {
  const response = await api.get('/faculty/reports/download', {
    params: { type, name },
    responseType: 'blob',
  });
  return response.data;
};

export interface GradeRow {
  letter: string;
  count: number;
}

export interface CoAttainmentRow {
  coCode: string;
  maxMarks: number;
  studentsAttained: number;
  attainmentPercent: number;
  remarks: string;
}

export interface TopicRow {
  topic: string;
  hours: number;
  instructor: string;
}

export interface AssessmentMethodRow {
  method: string;
  percent: number;
  readOnly: boolean;
}

export interface ActionPlanRow {
  action: string;
  completionDate: string;
  responsible: string;
}

// A faithful (if large) port of the desktop app's Course Report form - it's a
// self-assessment document, so almost every field here is free text or a manually
// typed number rather than something computed from marks.
export interface CourseReportForm {
  lectureHours: string;
  tutorialHours: string;
  practicalHours: string;
  instructors: string;
  hodName: string;
  studentsCompleting: string;
  percentPassed: string;
  percentFailed: string;
  gradeDistribution: GradeRow[];
  coAttainment: CoAttainmentRow[];
  topics: TopicRow[];
  coverageLevel: string;
  topicDeviation: string;
  methodLectures: boolean;
  methodLab: boolean;
  methodSeminar: boolean;
  methodActivity: boolean;
  methodCaseStudy: boolean;
  methodAssignment: boolean;
  otherMethods: string;
  assessmentMethods: AssessmentMethodRow[];
  facilitiesLevel: string;
  inadequacies: string;
  adminConstraints: string;
  studentCriticism: string;
  moderatorComments: string;
  externalComments: string;
  enhancementProgress: string;
  actionPlan: ActionPlanRow[];
  thresholdOverride: string;
}

// Auto-filled values the form seeds itself with - course info, enrolled count, the
// course's configured CO codes, the fixed letter-grade set, this assignment's
// threshold default, next year's label for the action-plan heading, and the 6 fixed
// assessment-method row names. Everything else in the form starts blank/zero.
export interface CourseReportSeed {
  courseName: string;
  credits: number | null;
  semester: string;
  department: string;
  totalStudents: number;
  instructorDefault: string;
  coCodes: string[];
  letterGrades: string[];
  coCohortThresholdDefault: number;
  nextAcademicYear: string;
  assessmentMethodDefaults: AssessmentMethodRow[];
}

export interface CourseReportContext {
  seed: CourseReportSeed;
  draft: CourseReportForm | null;
  updatedAt: string | null;
}

export interface GenerateCourseReportResult {
  pdfFileName: string | null;
  issues: string[];
}

const courseReportUrl = (courseCode: string, programme: string, academicYear: string, department: string) =>
  `/faculty/course-report/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`;

export const getCourseReportContext = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.get<CourseReportContext>(courseReportUrl(courseCode, programme, academicYear, department));

export const saveCourseReportDraft = (
  courseCode: string, programme: string, academicYear: string, department: string, form: CourseReportForm,
) => api.put<{ updatedAt: string }>(courseReportUrl(courseCode, programme, academicYear, department), form);

export const clearCourseReportDraft = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.delete(courseReportUrl(courseCode, programme, academicYear, department));

export const generateCourseReport = (
  courseCode: string, programme: string, academicYear: string, department: string, form: CourseReportForm,
) => api.post<GenerateCourseReportResult>(`${courseReportUrl(courseCode, programme, academicYear, department)}/generate`, form);

export interface EvaluationRow {
  studentId: string;
  studentName: string;
  obtainedByCo: Record<string, number>;
}

export interface CoHistogramRow {
  coCode: string;
  bucketCounts: number[];
}

export interface AttainmentStatusRow {
  coCode: string;
  achievedPercent: number;
  satisfied: boolean;
}

export interface SummaryReportPreview {
  courseName: string;
  credits: number | null;
  semester: string;
  majorityBatch: number;
  totalStudents: number;
  coIndividualThreshold: number;
  coCohortThreshold: number;
  coCodes: string[];
  coMaxMarks: Record<string, number>;
  evaluationSheet: EvaluationRow[];
  histogram: CoHistogramRow[];
  thresholdInStandardRange: boolean;
  attainmentStatus: AttainmentStatusRow[];
  issues: string[];
}

export interface GenerateSummaryReportRequest {
  feedback1: string;
  feedback2: string;
  improvementPlan: string;
}

export interface GenerateSummaryReportResult {
  pdfFileName: string | null;
  issues: string[];
}

const summaryReportUrl = (courseCode: string, programme: string, academicYear: string, department: string) =>
  `/faculty/summary-report/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`;

export const getSummaryReportPreview = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.get<SummaryReportPreview>(summaryReportUrl(courseCode, programme, academicYear, department));

export const generateSummaryReport = (
  courseCode: string, programme: string, academicYear: string, department: string, request: GenerateSummaryReportRequest,
) => api.post<GenerateSummaryReportResult>(`${summaryReportUrl(courseCode, programme, academicYear, department)}/generate`, request);

export interface MarksReportResult {
  fileName: string | null;
  issues: string[];
}

const marksReportUrl = (courseCode: string, programme: string, academicYear: string, department: string) =>
  `/faculty/marks-report/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`;

// Both reports are read-only computed dumps (no faculty free text), so unlike the
// other reports there's nothing to preview on screen first - pick sections, generate,
// download, same as the desktop app's own pickers.
export const generateDetailedMarksReport = (
  courseCode: string, programme: string, academicYear: string, department: string,
  sectionIds: number[], includeOverallCoAttainment: boolean,
) => api.post<MarksReportResult>(`${marksReportUrl(courseCode, programme, academicYear, department)}/detailed`, {
  sectionIds, includeOverallCoAttainment,
});

export const generateConsolidatedMarksReport = (
  courseCode: string, programme: string, academicYear: string, department: string, sectionIds: number[],
) => api.post<MarksReportResult>(`${marksReportUrl(courseCode, programme, academicYear, department)}/consolidated`, {
  sectionIds,
});

export interface StudentResultRow {
  studentId: string;
  studentName: string;
  batch: number;
  attendanceWeighted: number;
  quizAssignmentWeighted: number;
  midWeighted: number;
  finalWeighted: number;
  totalPercentage: number;
  letterGrade: string;
  gradePoint: number;
  passed: boolean;
}

export interface GradeDistributionRow {
  letterGrade: string;
  count: number;
  percentage: number;
}

export interface ResultStatistics {
  totalStudents: number;
  passedCount: number;
  failedCount: number;
  passPercentage: number;
  failPercentage: number;
  averagePercentage: number;
  averageGpa: number;
  gradeDistribution: GradeDistributionRow[];
}

export interface CourseResultData {
  majorityBatch: number;
  batchBelow23: boolean;
  results: StudentResultRow[];
  statistics: ResultStatistics;
}

export interface ResultsOutcome {
  data: CourseResultData | null;
  issue: string | null;
}

export const getCourseResults = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.get<ResultsOutcome>(
    `/faculty/results/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`,
  );

export interface MyReportFile {
  type: string;
  name: string;
  sizeBytes: number;
  lastModified: string;
}

// Best-effort: matches each on-disk report's filename against this faculty's own
// course assignments, since the shared report folders don't record a per-file owner.
// See FacultyReportService.listMyReports for the full explanation.
export const listMyReports = () => api.get<MyReportFile[]>('/faculty/reports/mine');

export interface MarksImportResult {
  marksSaved: number;
  attendanceSaved: number;
  errors: string[];
}

const marksExcelUrl = (courseCode: string, programme: string, academicYear: string) =>
  `/faculty/marks-excel/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}`;

export const exportMarksExcel = async (courseCode: string, programme: string, academicYear: string) => {
  const response = await api.get(`${marksExcelUrl(courseCode, programme, academicYear)}/export`, {
    responseType: 'blob',
  });
  return response.data as Blob;
};

export const importMarksExcel = (courseCode: string, programme: string, academicYear: string, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<MarksImportResult>(`${marksExcelUrl(courseCode, programme, academicYear)}/import`, form);
};

export interface AssessmentSectionSummary {
  sectionName: string;
  questionCount: number;
  totalMarks: number;
  marksEntered: number;
  totalPossibleEntries: number;
}

export interface CourseSummary {
  courseCode: string;
  courseName: string;
  programme: string;
  academicYear: string;
  enrolledStudents: number;
  sections: AssessmentSectionSummary[];
  totalQuestions: number;
  totalMarks: number;
  completionPercentage: number;
}

export const getCourseSummary = (courseCode: string, programme: string, academicYear: string) =>
  api.get<CourseSummary>(
    `/faculty/course-summary/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}`,
  );
