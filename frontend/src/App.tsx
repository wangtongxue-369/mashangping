import { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import ProtectedRoute from './layout/ProtectedRoute';

const LoginPage = lazy(() => import('./pages/LoginPage'));
const TeacherLayout = lazy(() => import('./layout/TeacherLayout'));
const CoursesPage = lazy(() => import('./pages/teacher/CoursesPage'));
const CourseStudentsPage = lazy(() => import('./pages/teacher/CourseStudentsPage'));
const ProblemsPage = lazy(() => import('./pages/teacher/ProblemsPage'));
const ProblemDetailPage = lazy(() => import('./pages/teacher/ProblemDetailPage'));

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/teacher',
    element: (
      <ProtectedRoute>
        <TeacherLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="/teacher/courses" replace /> },
      { path: 'courses', element: <CoursesPage /> },
      { path: 'courses/:courseId/students', element: <CourseStudentsPage /> },
      { path: 'problems', element: <ProblemsPage /> },
      { path: 'problems/:problemId', element: <ProblemDetailPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/login" replace /> },
]);

export default router;
