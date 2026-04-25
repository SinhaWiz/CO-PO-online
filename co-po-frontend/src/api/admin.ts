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
export const deleteCourse = (courseCode: string, programme: string) => api.delete(`/admin/courses/${courseCode}/${programme}`);
