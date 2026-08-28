import { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import ProtectedRoute from './layout/ProtectedRoute';

const LoginPage = lazy(() => import('./pages/LoginPage'));
const TeacherLayout = lazy(() => import('./layout/TeacherLayout'));

/** 占位页：后续任务接入课程/题库真实页面。 */
function Placeholder({ title }: { title: string }) {
  return <div style={{ background: '#fff', padding: 24 }}>{title}（Task 3+ 页面接入）</div>;
}

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
      { path: 'courses', element: <Placeholder title="课程管理" /> },
      { path: 'problems', element: <Placeholder title="题库管理" /> },
    ],
  },
  { path: '*', element: <Navigate to="/login" replace /> },
]);

export default router;