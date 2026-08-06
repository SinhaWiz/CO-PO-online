import { Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';

import AdminLayout from './pages/admin/AdminLayout';
import ManageFaculties from './pages/admin/ManageFaculties';
import ManageStudents from './pages/admin/ManageStudents';
import ManageCourses from './pages/admin/ManageCourses';
import ManageEnrollments from './pages/admin/ManageEnrollments';
import ManageCourseAssignments from './pages/admin/ManageCourseAssignments';
import ManageCulminationCourses from './pages/admin/ManageCulminationCourses';
import ManageThresholds from './pages/admin/ManageThresholds';
import ManageGraduatingStudents from './pages/admin/ManageGraduatingStudents';
import CohortPOReport from './pages/admin/CohortPOReport';
import ViewReports from './pages/admin/ViewReports';
import ChangePassword from './pages/admin/ChangePassword';
import ManageAdmins from './pages/admin/ManageAdmins';

import FacultyLayout from './pages/faculty/FacultyLayout';
import ManageAssessments from './pages/faculty/ManageAssessments';
import EnterMarks from './pages/faculty/EnterMarks';
import FacultyChangePassword from './pages/faculty/ChangePassword';

// Components
import AdminDashboardHome from './pages/admin/AdminDashboardHome';
import FacultyDashboardHome from './pages/faculty/FacultyDashboardHome';

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        
        <Route 
          path="/admin/*" 
          element={
            <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
              <AdminLayout />
            </ProtectedRoute>
          } 
        >
          <Route index element={<AdminDashboardHome />} />
          <Route path="dashboard" element={<AdminDashboardHome />} />
          <Route path="faculties" element={<ManageFaculties />} />
          <Route path="students" element={<ManageStudents />} />
          <Route path="courses" element={<ManageCourses />} />
          <Route path="enrollments" element={<ManageEnrollments />} />
          <Route path="course-assignments" element={<ManageCourseAssignments />} />
          <Route path="culmination-courses" element={<ManageCulminationCourses />} />
          <Route path="thresholds" element={<ManageThresholds />} />
          <Route path="graduating-students" element={<ManageGraduatingStudents />} />
          <Route path="cohort-po-report" element={<CohortPOReport />} />
          <Route path="reports" element={<ViewReports />} />
          <Route path="change-password" element={<ChangePassword />} />
          <Route path="manage-admins" element={<ManageAdmins />} />
        </Route>
        
        <Route 
          path="/faculty/*" 
          element={
            <ProtectedRoute allowedRoles={['ROLE_FACULTY']}>
              <FacultyLayout />
            </ProtectedRoute>
          } 
        >
          <Route index element={<FacultyDashboardHome />} />
          <Route path="dashboard" element={<FacultyDashboardHome />} />
          <Route path="assessments" element={<ManageAssessments />} />
          <Route path="marks" element={<EnterMarks />} />
          <Route path="change-password" element={<FacultyChangePassword />} />
        </Route>

        {/* Default redirect to login */}
        <Route path="/" element={<Navigate to="/login" />} />
        <Route path="*" element={<Navigate to="/login" />} />
      </Routes>
    </AuthProvider>
  );
}

export default App;
