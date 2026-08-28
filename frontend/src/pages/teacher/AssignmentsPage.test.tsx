import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import AssignmentsPage from './AssignmentsPage';
import AssignmentDetailPage from './AssignmentDetailPage';
import ProtectedRoute from '../../layout/ProtectedRoute';
import { AuthProvider } from '../../auth/useAuth';

// 只替换 axios 实例，extractApiMessage 等纯函数保留真实实现。
const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, client: mocks };
});

/** 后端统一信封的 axios 响应形态。 */
function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

/**
 * Task 2 Harness 模式：ConfigProvider zh_CN（空态文案「暂无数据」）+ AntApp
 * （message 上下文）+ 独立 QueryClient + AuthProvider + 路由（含 ProtectedRoute）。
 */
function renderAt(path: string, routePath: string, element: React.ReactNode) {
  localStorage.setItem('msp_token', 'test-token');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={[path]}>
              <Routes>
                <Route path="/login" element={<div>登录页</div>} />
                <Route
                  path={routePath}
                  element={<ProtectedRoute>{element}</ProtectedRoute>}
                />
              </Routes>
            </MemoryRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>,
  );
}

describe('AssignmentsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('作业列表为空时表格渲染「暂无数据」，并按契约请求课程作业列表', async () => {
    mocks.get.mockResolvedValue(ok({ records: [], total: 0, size: 20, current: 1 }));
    renderAt(
      '/teacher/courses/3/assignments',
      '/teacher/courses/:courseId/assignments',
      <AssignmentsPage />,
    );
    // 空态文案同时出现在标题节点与 Empty 描述里，断言「至少渲染出一处」即可。
    expect((await screen.findAllByText('暂无数据')).length).toBeGreaterThan(0);
    expect(screen.getByText('新建作业')).toBeTruthy();
    expect(mocks.get).toHaveBeenCalledWith('/courses/3/assignments', {
      params: { page: 1, size: 20 },
    });
  });

  it('渲染作业行：标题链接详情、起止本地格式、宽限/题数/总分/状态与发布开关', async () => {
    mocks.get.mockResolvedValue(
      ok({
        records: [
          {
            id: 9,
            title: '第一次编程作业',
            startAt: '2026-08-01T08:00:00',
            dueAt: '2026-08-30T23:59:59',
            lateDays: 3,
            isPublished: true,
            problemCount: 2,
            totalScore: 50,
            status: 'IN_PROGRESS',
          },
        ],
        total: 1,
        size: 20,
        current: 1,
      }),
    );
    renderAt(
      '/teacher/courses/3/assignments',
      '/teacher/courses/:courseId/assignments',
      <AssignmentsPage />,
    );
    expect(await screen.findByText('第一次编程作业')).toBeTruthy();
    const link = screen.getByRole('link', { name: '第一次编程作业' });
    expect(link.getAttribute('href')).toBe('/teacher/assignments/9');
    expect(screen.getByText('2026-08-01 08:00 ~ 2026-08-30 23:59')).toBeTruthy();
    expect(screen.getByText('3 天')).toBeTruthy();
    expect(screen.getByText('进行中', { selector: 'span.ant-tag' })).toBeTruthy();
    // 发布开关为受控 Switch（button role=switch），已发布时 aria-checked 为 true
    expect(screen.getByRole('switch').getAttribute('aria-checked')).toBe('true');
  });
});

describe('AssignmentDetailPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('详情渲染已选题目行，添加选题 Modal 展示未选课程题目与分值输入', async () => {
    const detail = ok({
      id: 5,
      courseId: 3,
      title: '第一次作业',
      description: null,
      startAt: '2026-08-01T08:00:00',
      dueAt: '2026-08-30T23:59:59',
      lateDays: 3,
      isPublished: false,
      status: 'IN_PROGRESS',
      problems: [{ problemId: 11, title: '两数之和', score: 20, sortOrder: 1 }],
    });
    const courseProblems = ok({
      records: [
        {
          problemId: 21,
          title: '回文数',
          languages: ['CPP'],
          timeLimitMs: 1000,
          memoryLimitMb: 256,
          isPublic: true,
          testCaseCount: 4,
          sortOrder: 1,
        },
        {
          problemId: 22,
          title: '合并区间',
          languages: ['JAVA'],
          timeLimitMs: 2000,
          memoryLimitMb: 512,
          isPublic: false,
          testCaseCount: 3,
          sortOrder: 2,
        },
      ],
      total: 2,
      size: 10,
      current: 1,
    });
    mocks.get.mockImplementation((url: string) => {
      if (url === '/assignments/5') return detail;
      if (url === '/courses/3/problems') return courseProblems;
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });
    renderAt('/teacher/assignments/5', '/teacher/assignments/:assignmentId', <AssignmentDetailPage />);

    expect(await screen.findByText('第一次作业')).toBeTruthy();
    // 已选题目表渲染已选题行与当前分值
    expect(screen.getByText('两数之和')).toBeTruthy();
    expect(screen.getByDisplayValue('20')).toBeTruthy();
    // Task 6 入口链接按固定路径渲染
    expect(screen.getByRole('link', { name: /提交历史/ }).getAttribute('href')).toBe(
      '/teacher/assignments/5/submissions',
    );
    expect(screen.getByRole('link', { name: /成绩册/ }).getAttribute('href')).toBe(
      '/teacher/assignments/5/gradebook',
    );

    // 打开添加选题 Modal：已选题被排除，仅剩未选课程题目，且每行有分值输入
    fireEvent.click(screen.getByRole('button', { name: /添加选题/ }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('回文数')).toBeTruthy();
    expect(within(dialog).getByText('合并区间')).toBeTruthy();
    expect(within(dialog).queryByText('两数之和')).toBeNull();
    const scoreInputs = within(dialog).getAllByRole('spinbutton');
    expect(scoreInputs.length).toBe(2);
    await waitFor(() => {
      expect(mocks.get).toHaveBeenCalledWith('/courses/3/problems', { params: { page: 1, size: 10 } });
    });
  });
});
