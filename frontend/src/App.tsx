import { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import ProtectedRoute from './layout/ProtectedRoute';

const LoginPage = lazy(() => import('./pages/LoginPage'));
const TeacherLayout = lazy(() => import('./layout/TeacherLayout'));
const CoursesPage = lazy(() => import('./pages/teacher/CoursesPage'));
const CourseStudentsPage = lazy(() => import('./pages/teacher/CourseStudentsPage'));
const AssignmentsPage = lazy(() => import('./pages/teacher/AssignmentsPage'));
const AssignmentDetailPage = lazy(() => import('./pages/teacher/AssignmentDetailPage'));
const SubmissionsPage = lazy(() => import('./pages/teacher/SubmissionsPage'));
const GradebookPage = lazy(() => import('./pages/teacher/GradebookPage'));
const ProblemsPage = lazy(() => import('./pages/teacher/ProblemsPage'));
const ProblemDetailPage = lazy(() => import('./pages/teacher/ProblemDetailPage'));
const PlagiarismPage = lazy(() => import('./pages/teacher/PlagiarismPage'));
// 学生端路由骨架（lazy 页面本任务提供最小可编译占位，供后续任务填充）。
const StudentLayout = lazy(() => import('./layout/StudentLayout'));
const MyCoursesPage = lazy(() => import('./pages/student/MyCoursesPage'));
const CourseAssignmentsPage = lazy(() => import('./pages/student/CourseAssignmentsPage'));
const AssignmentProblemsPage = lazy(() => import('./pages/student/AssignmentProblemsPage'));
const CodingPage = lazy(() => import('./pages/student/CodingPage'));
const PracticePage = lazy(() => import('./pages/student/PracticePage'));
const PracticeCodingPage = lazy(() => import('./pages/student/PracticeCodingPage'));

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
      { path: 'courses/:courseId/assignments', element: <AssignmentsPage /> },
      { path: 'assignments/:assignmentId', element: <AssignmentDetailPage /> },
      { path: 'assignments/:assignmentId/submissions', element: <SubmissionsPage /> },
      { path: 'assignments/:assignmentId/gradebook', element: <GradebookPage /> },
      { path: 'assignments/:assignmentId/plagiarism/:problemId', element: <PlagiarismPage /> },
      { path: 'problems', element: <ProblemsPage /> },
      { path: 'problems/:problemId', element: <ProblemDetailPage /> },
    ],
  },
  {
    path: '/student',
    element: (
      <ProtectedRoute role="STUDENT">
        <StudentLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Navigate to="/student/courses" replace /> },
      { path: 'courses', element: <MyCoursesPage /> },
      { path: 'courses/:courseId/assignments', element: <CourseAssignmentsPage /> },
      { path: 'courses/:courseId/assignments/:assignmentId', element: <AssignmentProblemsPage /> },
      {
        path: 'courses/:courseId/assignments/:assignmentId/problems/:problemId',
        element: <CodingPage />,
      },
      { path: 'practice/problems', element: <PracticePage /> },
      { path: 'practice/problems/:problemId', element: <PracticeCodingPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/login" replace /> },
]);

export default router;
