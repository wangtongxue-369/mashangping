import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import AssignmentProblemsPage from './AssignmentProblemsPage';

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
      <MemoryRouter initialEntries={['/student/courses/7/assignments/3']}>
        <Routes>
          <Route path="/student/courses/:courseId/assignments/:assignmentId" element={<AssignmentProblemsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AssignmentProblemsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染题目行：标题/分值/语言标签，并提供返回入口', async () => {
    mocks.get.mockResolvedValue(
      ok([
        {
          assignmentProblemId: 5,
          problemId: 9,
          title: '两数之和',
          score: 20,
          sortOrder: 0,
          languages: ['C', 'CPP'],
          timeLimitMs: 1000,
          memoryLimitMb: 256,
        },
      ]),
    );
    renderPage();
    expect(await screen.findByText('两数之和')).toBeTruthy();
    expect(screen.getByText('C')).toBeTruthy();
    expect(screen.getByText('C++')).toBeTruthy();
    expect(screen.getByText('作业列表')).toBeTruthy();
  });
});
