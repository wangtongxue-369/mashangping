import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CourseConsolePage from './CourseConsolePage';

// jsdom 无 canvas，图表组件桩成空节点
vi.mock('@ant-design/plots', () => ({
  Line: () => null,
  Column: () => null,
  Pie: () => null,
  Area: () => null,
}));

const mocks = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, client: mocks };
});

function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

function mockData() {
  mocks.get.mockImplementation((url: string) => {
    if (url.includes('/analytics/overview')) {
      return ok({
        courseId: 7, studentCount: 3, assignmentCount: 2, publishedAssignmentCount: 1,
        courseProblemCount: 4, latestAvg: 82.5, highPlagiarismPairs: 1,
      });
    }
    if (url.includes('/analytics/score-trend')) {
      return ok([{ assignmentId: 1, title: '第一次作业', startAt: '', dueAt: '', avg: 82.5, max: 100, min: 40, submittedCount: 3 }]);
    }
    if (url.includes('/analytics/ac-rate')) {
      return ok([{ assignmentId: 1, assignmentTitle: '第一次作业', problemId: 9, problemTitle: 'A+B', acStudents: 2, submittedStudents: 3, rate: 0.67 }]);
    }
    if (url.includes('/analytics/status-distribution')) {
      return ok([{ status: 'AC', count: 2 }]);
    }
    if (url.includes('/analytics/submission-timeline')) {
      return ok([{ date: '2026-09-06', count: 2 }]);
    }
    if (url.includes('/analytics/plagiarism-risk')) {
      return ok([]);
    }
    if (url.includes('/analytics/score-distribution')) {
      return ok({ assignmentId: 1, assignmentTitle: '第一次作业', buckets: [{ label: '90-100', count: 2 }] });
    }
    return Promise.reject(new Error(`unhandled ${url}`));
  });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/teacher/courses/7']}>
        <Routes>
          <Route path="/teacher/courses/:courseId" element={<CourseConsolePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('CourseConsolePage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染概览卡与看板标题', async () => {
    mockData();
    renderPage();
    expect(await screen.findByText(/在册学生/)).toBeTruthy();
    expect(screen.getByText(/作业 \/ 已发布/)).toBeTruthy();
    // Statistic 数值会拆分为多个 span，按整段文本断言
    expect(document.body.textContent).toContain('82.5');
    expect(screen.getByText(/查重高疑似对/)).toBeTruthy();
    expect(screen.getByText(/各作业成绩趋势/)).toBeTruthy();
    expect(screen.getByText(/提交活跃时间线/)).toBeTruthy();
    expect(screen.getByText(/查重风险汇总/)).toBeTruthy();
  });

  it('无查重风险时展示空态', async () => {
    mockData();
    renderPage();
    expect(await screen.findByText(/当前无高疑似\/关注的查重对/)).toBeTruthy();
  });

  it('管理入口渲染', async () => {
    mockData();
    renderPage();
    expect(await screen.findByText('学生名单')).toBeTruthy();
    expect(screen.getByText('作业管理')).toBeTruthy();
  });
});
