import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './views/components/ProtectedRoute';
import LoginPage from './views/pages/LoginPage';
import RegisterPage from './views/pages/RegisterPage';
import DashboardPage from './views/pages/DashboardPage';
import AdminUserManagementPage from './views/pages/AdminUserManagementPage';
import UnauthorizedPage from './views/pages/UnauthorizedPage';
import ForgotPasswordPage from './views/pages/ForgotPasswordPage';
import ResetPasswordPage from './views/pages/ResetPasswordPage';
import CvPage from './views/pages/CvPage';

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
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/cv" element={<CvPage />} />
          </Route>

          {/* Admin-only routes */}
          <Route element={<ProtectedRoute allowedRoles={['ROLE_ADMIN']} />}>
            <Route path="/admin/users" element={<AdminUserManagementPage />} />
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
