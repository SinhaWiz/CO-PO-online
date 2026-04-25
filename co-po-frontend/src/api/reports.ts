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
