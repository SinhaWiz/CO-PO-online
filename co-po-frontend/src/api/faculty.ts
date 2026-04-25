import api from './axios';

export interface AssessmentQuestion {
  id?: number;
  assessmentId: number;
  title: string;
  marks: number;
  coId?: number;
  poId?: number;
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
