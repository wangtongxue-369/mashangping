import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { AuthProvider } from '../auth/useAuth';

function renderGated() {
  return render(
    <AuthProvider>
      <MemoryRouter
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
        initialEntries={['/teacher/courses']}
      >
        <Routes>
          <Route path="/login" element={<div>登录页</div>} />
          <Route
            path="/teacher/courses"
            element={
              <ProtectedRoute>
                <div>教师课程页</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('ProtectedRoute', () => {
  it('无 token 时重定向到 /login（受保护内容不渲染）', async () => {
    localStorage.clear();
    renderGated();
    expect(await screen.findByText('登录页')).toBeTruthy();
    expect(screen.queryByText('教师课程页')).toBeNull();
  });

  it('有 token 时渲染受保护内容（不跳 /login）', async () => {
    localStorage.setItem('msp_token', 'test-token');
    renderGated();
    expect(await screen.findByText('教师课程页')).toBeTruthy();
    expect(screen.queryByText('登录页')).toBeNull();
  });
});