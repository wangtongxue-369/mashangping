import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import CoursesPage from './CoursesPage';
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
function renderCoursesPage(noToken = false) {
  if (!noToken) localStorage.setItem('msp_token', 'test-token');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
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
                      <CoursesPage />
                    </ProtectedRoute>
                  }
                />
              </Routes>
            </MemoryRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>,
  );
}

describe('CoursesPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('课程列表为空时表格渲染「暂无数据」', async () => {
    mocks.get.mockResolvedValue(ok({ records: [], total: 0, size: 20, current: 1 }));
    renderCoursesPage();
    // 空态文案同时出现在标题节点与 Empty 描述里，断言「至少渲染出一处」即可。
    expect((await screen.findAllByText('暂无数据')).length).toBeGreaterThan(0);
    expect(screen.getByText('新建课程')).toBeTruthy();
  });

  it('渲染课程行：名称/学期展示，名称链接到学生名单页', async () => {
    mocks.get.mockResolvedValue(
      ok({
        records: [{ id: 7, name: '线性代数', term: '2026秋', description: '矩阵与行列式' }],
        total: 1,
        size: 20,
        current: 1,
      }),
    );
    renderCoursesPage();
    expect(await screen.findByText('线性代数')).toBeTruthy();
    expect(screen.getByText('2026秋')).toBeTruthy();
    const link = screen.getByRole('link', { name: '线性代数' });
    expect(link.getAttribute('href')).toBe('/teacher/courses/7/students');
  });

  it('无 token 时经 ProtectedRoute 重定向 /login，课程页不渲染且不发请求', async () => {
    mocks.get.mockResolvedValue(ok({ records: [], total: 0, size: 20, current: 1 }));
    renderCoursesPage(true);
    expect(await screen.findByText('登录页')).toBeTruthy();
    expect(screen.queryByText('新建课程')).toBeNull();
    expect(mocks.get).not.toHaveBeenCalled();
  });
});
