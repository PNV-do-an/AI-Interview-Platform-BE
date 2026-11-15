import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './views/components/ProtectedRoute';
import AppLayout from './views/components/AppLayout';
import LoginPage from './views/pages/LoginPage';
import RegisterPage from './views/pages/RegisterPage';
import DashboardPage from './views/pages/DashboardPage';
import CvPage from './views/pages/CvPage';
import StudyProgramPage from './views/pages/StudyProgramPage';
import StudyTopicPage from './views/pages/StudyTopicPage';
import AdminUserManagementPage from './views/pages/AdminUserManagementPage';
import AdminPositionPage from './views/pages/AdminPositionPage';
import UnauthorizedPage from './views/pages/UnauthorizedPage';
import ForgotPasswordPage from './views/pages/ForgotPasswordPage';
import ResetPasswordPage from './views/pages/ResetPasswordPage';

const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          {/* Protected routes — any authenticated user */}
          <Route element={<ProtectedRoute />}>
            {/* Dashboard renders inside the shared app shell (sidebar + top bar) */}
            <Route element={<AppLayout />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/study" element={<StudyProgramPage />} />
              <Route path="/study/programs/:programId" element={<StudyProgramPage />} />
              <Route
                path="/study/programs/:programId/topics/:topicId"
                element={<StudyTopicPage />}
              />
            </Route>
            {/* CvPage ships its own full-page layout, so it is not nested in AppLayout */}
            <Route path="/cv" element={<CvPage />} />
          </Route>

          {/* Admin-only routes */}
          <Route element={<ProtectedRoute allowedRoles={['ROLE_ADMIN']} />}>
            <Route element={<AppLayout />}>
              <Route path="/admin/users" element={<AdminUserManagementPage />} />
              <Route path="/admin/positions" element={<AdminPositionPage />} />
            </Route>
          </Route>

          {/* Unauthorized */}
          <Route path="/unauthorized" element={<UnauthorizedPage />} />

          {/* Default redirect */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
};

export default App;
