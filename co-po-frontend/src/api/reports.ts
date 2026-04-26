import api from './axios';

// Export Excel
export const downloadCourseExcel = async () => {
    const response = await api.get('/reports/courses/excel', {
        responseType: 'blob', // Important for downloading binary files
    });
    return response.data;
};

// Export PDF
export const downloadCoursePdf = async () => {
    const response = await api.get('/reports/courses/pdf', {
        responseType: 'blob', 
    });
    return response.data;
};

// Summarize Courses
export const summarizeCoursesWithAI = () => api.post<{ summary: string }>('/reports/courses/summarize', {
    projectId: 'demo-project',
    location: 'us-central1'
});

export interface CohortStudent {
    id: string;
    name: string;
}

export interface CohortPoRow {
    po: string;
    percent: number;
}

export interface CohortPoReportResult {
    rows: CohortPoRow[];
    statusMessages: string[];
    pdfFileName: string | null;
}

export interface ReportFileItem {
    type: 'CO' | 'PO' | 'Detailed' | 'Cohort PO' | string;
    name: string;
    sizeBytes: number;
    lastModified: string;
}

// Admin report APIs
export const getCohortProgrammes = () => api.get<string[]>('/admin/reports/cohort/programmes');

export const getCohortBatches = (programme: string) =>
    api.get<number[]>('/admin/reports/cohort/batches', { params: { programme } });

export const getCohortStudents = (programme: string, batch: number) =>
    api.get<CohortStudent[]>('/admin/reports/cohort/students', { params: { programme, batch } });

export const generateCohortPoReport = (programme: string, batch: number) =>
    api.post<CohortPoReportResult>('/admin/reports/cohort/generate', { programme, batch });

export const listAdminReports = () => api.get<ReportFileItem[]>('/admin/reports/files');

export const downloadAdminReport = async (type: string, name: string) => {
    const response = await api.get('/admin/reports/files/download', {
        params: { type, name },
        responseType: 'blob',
    });
    return response.data;
};
