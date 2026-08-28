import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { AuthProvider } from '../auth/useAuth';

function Harness() {
  return (
    <AuthProvider>
      <MemoryRouter initialEntries={['/teacher/courses']}>
        <ProtectedRoute>
          <div>已登录内容</div>
        </ProtectedRoute>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe('ProtectedRoute', () => {
  it('无 token 时重定向到 /login', () => {
    localStorage.clear();
    render(<Harness />);
    expect(screen.queryByText('已登录内容')).toBeNull();
  });
});