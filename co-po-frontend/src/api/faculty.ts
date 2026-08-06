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
