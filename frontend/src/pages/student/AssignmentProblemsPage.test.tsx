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

const problemRows = [
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
];

function header(status: string, extra: Record<string, unknown> = {}) {
  return {
    courseId: 7,
    courseName: '数据结构',
    assignmentId: 3,
    title: '第一次作业',
    status,
    startAt: '2026-09-01T00:00:00',
    dueAt: '2099-01-01T00:00:00',
    lateDays: 0,
    problemCount: 1,
    ...extra,
  };
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

function mockUrls(status: string, extra: Record<string, unknown> = {}) {
  mocks.get.mockImplementation((url: string) => {
    if (url === '/assignments/3') return ok(header(status, extra));
    return ok(problemRows);
  });
}

describe('AssignmentProblemsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染题目行：标题/分值/语言标签，页眉带课程名与作业名与状态徽标', async () => {
    mockUrls('IN_PROGRESS');
    renderPage();
    expect(await screen.findByText('两数之和')).toBeTruthy();
    expect(screen.getByText('C')).toBeTruthy();
    expect(screen.getByText('C++')).toBeTruthy();
    expect(screen.getByText('作业列表')).toBeTruthy();
    expect(screen.getByText('数据结构 · 第一次作业')).toBeTruthy();
    expect(screen.getByText('进行中')).toBeTruthy();
  });

  it('CLOSED 作业提示不可再提交但仍展示题目列表', async () => {
    mockUrls('CLOSED');
    renderPage();
    expect(await screen.findByText('两数之和')).toBeTruthy();
    expect(screen.getByText('已截止')).toBeTruthy();
    expect(screen.getByText(/作业已截止，仍可查看题目与历史提交/)).toBeTruthy();
  });

  it('LATE_WINDOW 作业提示将标记迟交', async () => {
    mockUrls('LATE_WINDOW', { lateDays: 2 });
    renderPage();
    expect(await screen.findByText('两数之和')).toBeTruthy();
    expect(screen.getByText('宽限中')).toBeTruthy();
    expect(screen.getByText(/已过截止时间，仍在宽限期内/)).toBeTruthy();
  });
});
