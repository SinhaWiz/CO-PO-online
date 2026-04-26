import api from './axios';

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
export const deleteFaculty = (id: string) => api.delete(`/admin/faculties/${id}`);

// Students API
export const getStudents = () => api.get<Student[]>('/admin/students');
export const createStudent = (data: Student) => api.post<Student>('/admin/students', data);
export const deleteStudent = (id: string) => api.delete(`/admin/students/${id}`);

// Courses API
export const getCourses = () => api.get<Course[]>('/admin/courses');
export const createCourse = (data: Course) => api.post<Course>('/admin/courses', data);
export const updateCourse = (courseCode: string, programme: string, data: Course) =>
  api.put<Course>(`/admin/courses/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}`, data);
export const deleteCourse = (courseCode: string, programme: string) =>
  api.delete(`/admin/courses/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}`);

// Enrollments API
export const getEnrollments = () => api.get<Enrollment[]>('/admin/enrollments');
export const createEnrollment = (data: Enrollment) => api.post<Enrollment>('/admin/enrollments', data);
export const deleteEnrollment = (studentId: string, courseId: string, programme: string, academicYear: string) =>
  api.delete(
    `/admin/enrollments/${encodeURIComponent(studentId)}/${encodeURIComponent(courseId)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}`,
  );

// Course assignments API
export const getCourseAssignments = () => api.get<CourseAssignment[]>('/admin/assignments');
export const createCourseAssignment = (data: CourseAssignment) => api.post<CourseAssignment>('/admin/assignments', data);
export const deleteCourseAssignment = (courseCode: string, programme: string, academicYear: string, department: string) =>
  api.delete(
    `/admin/assignments/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}/${encodeURIComponent(department)}`,
  );

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
