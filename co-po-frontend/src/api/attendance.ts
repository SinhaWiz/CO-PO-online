import api from './axios';

export interface AttendanceRow {
  studentId: string;
  studentName: string;
  attendancePercentage: number | null;
}

export interface AttendanceStatus {
  legacyOffering: boolean;
  majorityBatch: number | null;
  rows: AttendanceRow[];
}

export interface AttendanceEntry {
  studentId: string;
  attendancePercentage: number;
}

export interface AttendanceSaveResult {
  saved: number;
  errors: string[];
}

const attendanceUrl = (courseCode: string, programme: string, academicYear: string) =>
  `/faculty/attendance/${encodeURIComponent(courseCode)}/${encodeURIComponent(programme)}/${encodeURIComponent(academicYear)}`;

export const getAttendanceStatus = (courseCode: string, programme: string, academicYear: string) =>
  api.get<AttendanceStatus>(attendanceUrl(courseCode, programme, academicYear));

export const saveAttendanceBatch = (courseCode: string, programme: string, academicYear: string, entries: AttendanceEntry[]) =>
  api.post<AttendanceSaveResult>(attendanceUrl(courseCode, programme, academicYear), { entries });
