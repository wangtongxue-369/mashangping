import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { AuthProvider } from '../auth/useAuth';

/**
 * 与 App.tsx 一致的守卫拓扑：/student/courses 挂 role=STUDENT，
 * /teacher/courses 挂 role=TEACHER，/any 不传 role（仅 token 校验的默认行为）。
 */
function renderGate(initialPath: string) {
  return render(
    <AuthProvider>
      <MemoryRouter
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
        initialEntries={[initialPath]}
      >
        <Routes>
          <Route path="/login" element={<div>登录页</div>} />
          <Route
            path="/student/courses"
            element={
              <ProtectedRoute role="STUDENT">
                <div>学生课程页</div>
              </ProtectedRoute>
            }
          />
          <Route
            path="/teacher/courses"
            element={
              <ProtectedRoute role="TEACHER">
                <div>教师课程页</div>
              </ProtectedRoute>
            }
          />
          <Route
            path="/any"
            element={
              <ProtectedRoute>
                <div>任意内容页</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

/** 写入登录态：token + msp_user（AuthProvider 初始化读取）。 */
function setStoredUser(role: string) {
  localStorage.setItem('msp_token', 'test-token');
  localStorage.setItem(
    'msp_user',
    JSON.stringify({ id: 1, username: 'u', realName: '用户', studentNo: '', role }),
  );
}

describe('ProtectedRoute', () => {
  it('无 token 时重定向到 /login（受保护内容不渲染）', async () => {
    localStorage.clear();
    renderGate('/teacher/courses');
    expect(await screen.findByText('登录页')).toBeTruthy();
    expect(screen.queryByText('教师课程页')).toBeNull();
  });

  it('有 token 且角色相符时渲染受保护内容（不跳 /login）', async () => {
    setStoredUser('TEACHER');
    renderGate('/teacher/courses');
    expect(await screen.findByText('教师课程页')).toBeTruthy();
    expect(screen.queryByText('登录页')).toBeNull();
  });

  it('未传 role 时仅做 token 校验（角色不符也放行，兼容既有用法）', async () => {
    setStoredUser('STUDENT');
    renderGate('/any');
    expect(await screen.findByText('任意内容页')).toBeTruthy();
  });

  it('STUDENT 访问教师守卫路由时重定向到自己的 /student/courses', async () => {
    setStoredUser('STUDENT');
    renderGate('/teacher/courses');
    expect(await screen.findByText('学生课程页')).toBeTruthy();
    expect(screen.queryByText('教师课程页')).toBeNull();
  });

  it('TEACHER 访问学生守卫路由时重定向到自己的 /teacher/courses', async () => {
    setStoredUser('TEACHER');
    renderGate('/student/courses');
    expect(await screen.findByText('教师课程页')).toBeTruthy();
    expect(screen.queryByText('学生课程页')).toBeNull();
  });
});
