import api from './axios';

export interface RosterQuestion {
  id: number;
  title: string;
  maxMarks: number;
}

export interface RosterCell {
  questionId: number;
  marksObtained: number | null;
}

export interface RosterRow {
  studentId: string;
  studentName: string;
  cells: RosterCell[];
}

export interface Roster {
  questions: RosterQuestion[];
  rows: RosterRow[];
}

export interface MarkEntry {
  studentId: string;
  questionId: number;
  marksObtained: number;
}

export interface BatchSaveResult {
  saved: number;
  errors: string[];
}

export const getRoster = (assessmentId: number) =>
  api.get<Roster>(`/faculty/gradebook/${assessmentId}/roster`);

export const saveMarksBatch = (assessmentId: number, entries: MarkEntry[]) =>
  api.post<BatchSaveResult>(`/faculty/gradebook/${assessmentId}/marks/batch`, { entries });
