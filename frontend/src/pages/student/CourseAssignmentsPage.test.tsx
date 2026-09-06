import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CourseAssignmentsPage from './CourseAssignmentsPage';

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }));

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, client: mocks };
});

function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/student/courses/7/assignments']}>
        <Routes>
          <Route path="/student/courses/:courseId/assignments" element={<CourseAssignmentsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('CourseAssignmentsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染作业行：标题/状态徽标（未开始、进行中），并提供返回入口', async () => {
    mocks.get.mockResolvedValue(
      ok({
        records: [
          { id: 1, title: '未开始作业', startAt: '2026-09-10T00:00', dueAt: '2026-09-20T00:00', lateDays: 0, problemCount: 3, status: 'NOT_STARTED' },
          { id: 2, title: '进行中作业', startAt: '2026-09-01T00:00', dueAt: '2026-09-15T00:00', lateDays: 2, problemCount: 5, status: 'IN_PROGRESS' },
        ],
        total: 2,
        size: 20,
        current: 1,
      }),
    );
    renderPage();
    expect(await screen.findByText('进行中作业')).toBeTruthy();
    expect(screen.getByText('未开始作业')).toBeTruthy();
    expect(screen.getByText('未开始')).toBeTruthy();
    expect(screen.getByText('进行中')).toBeTruthy();
    expect(screen.getByText('我的课程')).toBeTruthy();
    // 未开始作业不可进入：标题不是链接/按钮；进行中作业是可点击入口（按钮）。
    expect(screen.getByText('未开始作业').closest('button')).toBeNull();
    expect(screen.getByText('未开始作业').closest('a')).toBeNull();
    expect(screen.getByText('进行中作业').closest('button')).not.toBeNull();
  });
});
