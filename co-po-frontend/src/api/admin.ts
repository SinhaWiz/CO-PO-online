import api from './axios';

export interface ImportResult {
  inserted: number;
  skipped: number;
  mapped: number;
  errors: string[];
}

// Interfaces matching Backend Entities
export interface Faculty {
  id: string;
  shortname: string;
  fullName: string;
  email: string;
  password?: string;
}

export interface Student {
  id: string;
  batch: number;
  name: string;
  email: string;
  department: string;
  programme: string;
}

export interface Course {
  courseCode: string;
  programme: string;
  courseName: string;
  credits: number;
  department: string;
}

export interface CourseOutcome {
  id: number;
  coNumber: string;
}

export interface ProgramOutcome {
  id: number;
  poNumber: string;
}

export interface Enrollment {
  studentId: string;
  courseId: string;
  programme: string;
  academicYear: string;
}

export interface CourseAssignment {
  facultyId: string;
  courseCode: string;
  programme: string;
  academicYear: string;
  department: string;
}

export interface Thresholds {
  coIndividual: number;
  poIndividual: number;
  coCohort: number;
  poCohort: number;
}

export interface CulminationCourseItem {
  courseCode: string;
  courseName: string;
  display: string;
}

export interface CulminationSaveResult {
  saved: boolean;
  missingPOs: string[];
}

export interface GraduatingStudentRow {
  id: string;
  name: string;
  comment: string;
}

export interface GraduatingSaveResult {
  graduatingCount: number;
  commentedNonGraduatingCount: number;
}

export interface AdminProfile {
  id: number;
  email: string;
  isSuperAdmin: boolean;
  createdBy: string | null;
  createdAt: string | null;
}

export interface AdminListItem {
  id: number;
  email: string;
  isSuperAdmin: boolean;
  createdBy: string | null;
  createdAt: string | null;
}

export interface AdminsResponse {
  admins: AdminListItem[];
  total: number;
  superAdmins: number;
  regularAdmins: number;
}

// Faculties API
export const getFaculties = () => api.get<Faculty[]>('/admin/faculties');
export const createFaculty = (data: Faculty) => api.post<Faculty>('/admin/faculties', data);
export const updateFaculty = (id: string, data: Faculty) => api.put<Faculty>(`/admin/faculties/${id}`, data);
export const deleteFaculty = (id: string) => api.delete(`/admin/faculties/${id}`);
export const importFaculties = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<ImportResult>('/admin/faculties/import', form);
};

// Students API
export const getStudents = () => api.get<Student[]>('/admin/students');
export const createStudent = (data: Student) => api.post<Student>('/admin/students', data);
export const updateStudent = (id: string, data: Student) => api.put<Student>(`/admin/students/${id}`, data);
export const deleteStudent = (id: string) => api.delete(`/admin/students/${id}`);
export const importStudents = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<ImportResult>('/admin/students/import', form);
};

// Courses API
export const getCourses = () => api.get<Course[]>('/admin/courses');
export const createCourse = (data: Course) => api.post<Course>('/admin/courses', data);
export const updateCourse = (courseCode: string, programme: string, data: Course) =>
  api.put<Course>(`/admin/courses/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}`, data);
export const deleteCourse = (courseCode: string, programme: string) =>
  api.delete(`/admin/courses/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}`);
export const importCourses = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<ImportResult>('/admin/courses/import', form);
};

export interface CourseOutcomes {
  coIds: number[];
  poIds: number[];
}

export const getCourseOutcomes = (courseCode: string, programme: string) =>
  api.get<CourseOutcomes>(
    `/admin/courses/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/outcomes`,
  );
export const updateCourseOutcomes = (courseCode: string, programme: string, coIds: number[], poIds: number[]) =>
  api.put<CourseOutcomes>(
    `/admin/courses/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/outcomes`,
    { coIds, poIds },
  );

export interface CourseSection {
  id: number;
  courseCode: string;
  programme: string;
  displayName: string;
  sectionOrder: number;
}

const sectionsUrl = (courseCode: string, programme: string) =>
  `/admin/courses/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/sections`;

export const getCourseSections = (courseCode: string, programme: string) =>
  api.get<CourseSection[]>(sectionsUrl(courseCode, programme));
export const createCourseSection = (courseCode: string, programme: string, displayName: string, sectionOrder: number) =>
  api.post<CourseSection>(sectionsUrl(courseCode, programme), { displayName, sectionOrder });
export const updateCourseSection = (courseCode: string, programme: string, id: number, displayName: string, sectionOrder: number) =>
  api.put<CourseSection>(`${sectionsUrl(courseCode, programme)}/${id}`, { displayName, sectionOrder });
export const deleteCourseSection = (courseCode: string, programme: string, id: number) =>
  api.delete(`${sectionsUrl(courseCode, programme)}/${id}`);

// CO/PO master data API
export const getCOs = () => api.get<CourseOutcome[]>('/admin/outcomes/co');
export const createCO = (coNumber: string) => api.post<CourseOutcome>('/admin/outcomes/co', { coNumber });
export const deleteCO = (id: number) => api.delete(`/admin/outcomes/co/${id}`);

export const getPOs = () => api.get<ProgramOutcome[]>('/admin/outcomes/po');
export const createPO = (poNumber: string) => api.post<ProgramOutcome>('/admin/outcomes/po', { poNumber });
export const deletePO = (id: number) => api.delete(`/admin/outcomes/po/${id}`);

// Enrollments API
export const getEnrollments = () => api.get<Enrollment[]>('/admin/enrollments');
export const createEnrollment = (data: Enrollment) => api.post<Enrollment>('/admin/enrollments', data);
export const deleteEnrollment = (studentId: string, courseId: string, programme: string, academicYear: string) =>
  api.delete(
    `/admin/enrollments/${encodeURIComponent(studentId)}/${encodeURIComponent(courseId)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}`,
  );
export const importEnrollments = (
  file: File, defaultCourseCode?: string, defaultProgramme?: string, defaultAcademicYear?: string,
) => {
  const form = new FormData();
  form.append('file', file);
  if (defaultCourseCode) form.append('defaultCourseCode', defaultCourseCode);
  if (defaultProgramme) form.append('defaultProgramme', defaultProgramme);
  if (defaultAcademicYear) form.append('defaultAcademicYear', defaultAcademicYear);
  return api.post<ImportResult>('/admin/enrollments/import', form);
};

// Course assignments API
export const getCourseAssignments = () => api.get<CourseAssignment[]>('/admin/assignments');
export const createCourseAssignment = (data: CourseAssignment) => api.post<CourseAssignment>('/admin/assignments', data);
export const deleteCourseAssignment = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.delete(
    `/admin/assignments/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`,
  );
export const importCourseAssignments = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post<ImportResult>('/admin/assignments/import', form);
};

const assignmentThresholdsUrl = (courseCode: string, programme: string, academicYear: string, department: string) =>
  `/admin/assignments/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}/thresholds`;

export const getAssignmentThresholds = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.get<Thresholds>(assignmentThresholdsUrl(courseCode, programme, academicYear, department));
export const updateAssignmentThresholds = (
  courseCode: string, programme: string, academicYear: string, department: string, data: Thresholds,
) => api.put<Thresholds>(assignmentThresholdsUrl(courseCode, programme, academicYear, department), data);

// Configuration APIs
export const getThresholds = () => api.get<Thresholds>('/admin/config/thresholds');
export const updateThresholds = (data: Thresholds) => api.put('/admin/config/thresholds', data);

export const getCulminationProgrammes = () => api.get<string[]>('/admin/config/culmination/programmes');
export const getCulminationCoursesForProgramme = (programme: string) =>
  api.get<CulminationCourseItem[]>('/admin/config/culmination/courses', { params: { programme } });
export const getSelectedCulminationCourseCodes = (programme: string) =>
  api.get<string[]>('/admin/config/culmination/selected', { params: { programme } });
export const saveCulminationCourses = (programme: string, courseCodes: string[]) =>
  api.post<CulminationSaveResult>('/admin/config/culmination/save', { programme, courseCodes });

export const getGraduatingProgrammes = () => api.get<string[]>('/admin/config/graduating/programmes');
export const getGraduatingBatches = (programme: string) =>
  api.get<number[]>('/admin/config/graduating/batches', { params: { programme } });
export const getGraduatingStudents = (programme: string, batch: number) =>
  api.get<GraduatingStudentRow[]>('/admin/config/graduating/students', { params: { programme, batch } });
export const getGraduatingSelectedIds = (programme: string, batch: number) =>
  api.get<string[]>('/admin/config/graduating/selected', { params: { programme, batch } });
export const saveGraduatingStudents = (
  programme: string,
  batch: number,
  graduatingStudentIds: string[],
  commentsByStudentId: Record<string, string>,
) =>
  api.post<GraduatingSaveResult>('/admin/config/graduating/save', {
    programme,
    batch,
    graduatingStudentIds,
    commentsByStudentId,
  });

// Account APIs
export const getAdminProfile = () => api.get<AdminProfile>('/admin/account/me');

export const changeAdminPassword = (currentPassword: string, newPassword: string, confirmPassword: string) =>
  api.post<{ message: string }>('/admin/account/change-password', {
    currentPassword,
    newPassword,
    confirmPassword,
  });

export const getAdmins = () => api.get<AdminsResponse>('/admin/account/admins');

export const createAdminAccount = (email: string, password: string, confirmPassword: string) =>
  api.post<AdminListItem>('/admin/account/admins', {
    email,
    password,
    confirmPassword,
  });

export const deleteAdminAccount = (id: number, currentPassword: string) =>
  api.post<{ message: string }>(`/admin/account/admins/${id}/delete`, { currentPassword });

export const promoteAdminToSuper = (id: number, currentPassword: string) =>
  api.post<{ message: string }>(`/admin/account/admins/${id}/promote`, { currentPassword });

export const demoteAdminFromSuper = (id: number, currentPassword: string) =>
  api.post<{ message: string }>(`/admin/account/admins/${id}/demote`, { currentPassword });
