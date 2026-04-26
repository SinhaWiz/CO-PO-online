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
