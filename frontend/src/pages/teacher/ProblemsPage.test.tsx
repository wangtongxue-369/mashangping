import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import ProblemsPage from './ProblemsPage';
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
function renderProblemsPage(noToken = false) {
  if (!noToken) localStorage.setItem('msp_token', 'test-token');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <MemoryRouter
              future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
              initialEntries={['/teacher/problems']}
            >
              <Routes>
                <Route path="/login" element={<div>登录页</div>} />
                <Route
                  path="/teacher/problems"
                  element={
                    <ProtectedRoute>
                      <ProblemsPage />
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

describe('ProblemsPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('题库列表为空时表格渲染「暂无数据」', async () => {
    mocks.get.mockResolvedValue(ok({ records: [], total: 0, size: 20, current: 1 }));
    renderProblemsPage();
    // 空态文案同时出现在标题节点与 Empty 描述里，断言「至少渲染出一处」即可。
    expect((await screen.findAllByText('暂无数据')).length).toBeGreaterThan(0);
    expect(screen.getByText('新建题目')).toBeTruthy();
  });

  it('渲染题目行：标题链接到详情页，语言/公开为只读 Tag', async () => {
    mocks.get.mockResolvedValue(
      ok({
        records: [
          {
            id: 7,
            title: '两数之和',
            languages: ['CPP', 'JAVA'],
            timeLimitMs: 1000,
            memoryLimitMb: 256,
            isPublic: true,
            createdAt: '2026-08-27T10:30:00',
          },
        ],
        total: 1,
        size: 20,
        current: 1,
      }),
    );
    renderProblemsPage();
    expect(await screen.findByText('两数之和')).toBeTruthy();
    const link = screen.getByRole('link', { name: '两数之和' });
    expect(link.getAttribute('href')).toBe('/teacher/problems/7');
    // 语言键按中文标签展示；「公开」同时命中表头与 Tag，须按 Tag 选择器精确断言
    expect(screen.getByText('C++')).toBeTruthy();
    expect(screen.getByText('Java')).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: '公开' })).toBeTruthy();
    expect(screen.getByText('公开', { selector: 'span.ant-tag' })).toBeTruthy();
    expect(screen.getByText('2026-08-27 10:30')).toBeTruthy();
  });

  it('无 token 时经 ProtectedRoute 重定向 /login，题库页不渲染且不发请求', async () => {
    mocks.get.mockResolvedValue(ok({ records: [], total: 0, size: 20, current: 1 }));
    renderProblemsPage(true);
    expect(await screen.findByText('登录页')).toBeTruthy();
    expect(screen.queryByText('新建题目')).toBeNull();
    expect(mocks.get).not.toHaveBeenCalled();
  });

  it('新建题目：填写表单提交后按 ProblemUpsertRequest 契约 POST /problems', async () => {
    mocks.get.mockResolvedValue(ok({ records: [], total: 0, size: 20, current: 1 }));
    mocks.post.mockResolvedValue(ok({ id: 1, title: '两数之和' }));
    renderProblemsPage();

    fireEvent.click(await screen.findByText('新建题目'));
    await screen.findByRole('dialog'); // 等弹窗挂载再填表
    fireEvent.change(screen.getByLabelText('标题'), { target: { value: '两数之和' } });
    fireEvent.change(screen.getByPlaceholderText(/支持 Markdown/), {
      target: { value: '给定一个整数数组，求两数之和' },
    });
    // antd 按钮对两字文案自动插空格，可访问名为「创 建」，用正则匹配。
    fireEvent.click(screen.getByRole('button', { name: /创\s*建/ }));

    await waitFor(() => {
      expect(mocks.post).toHaveBeenCalledWith('/problems', {
        title: '两数之和',
        description: '给定一个整数数组，求两数之和',
        allowedLanguages: ['C', 'CPP', 'JAVA', 'PYTHON'],
        timeLimitMs: 1000,
        memoryLimitMb: 256,
        isPublic: false,
      });
    });
    // 提交成功提示 + invalidate 后列表重取
    expect(await screen.findByText('题目创建成功')).toBeTruthy();
    await waitFor(() => {
      expect(mocks.get).toHaveBeenCalledTimes(2);
    });
  });

  it('编辑题目：先拉详情回填表单，提交后按契约 PUT /problems/{id}', async () => {
    const list = ok({
      records: [
        {
          id: 7,
          title: '两数之和',
          languages: ['CPP', 'JAVA'],
          timeLimitMs: 2000,
          memoryLimitMb: 512,
          isPublic: true,
          createdAt: '2026-08-27T10:30:00',
        },
      ],
      total: 1,
      size: 20,
      current: 1,
    });
    const detail = ok({
      id: 7,
      title: '两数之和',
      description: '题干原文',
      languages: ['CPP', 'JAVA'],
      timeLimitMs: 2000,
      memoryLimitMb: 512,
      isPublic: true,
      testCases: [],
      createdAt: '2026-08-27T10:30:00',
      updatedAt: '2026-08-27T12:00:00',
    });
    mocks.get.mockResolvedValueOnce(list).mockResolvedValueOnce(detail);
    mocks.put.mockResolvedValue(ok(null));
    renderProblemsPage();

    fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    // 详情拉回后弹窗打开，表单已按详情回填
    await screen.findByRole('dialog');
    expect((screen.getByLabelText('标题') as HTMLInputElement).value).toBe('两数之和');
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => {
      expect(mocks.put).toHaveBeenCalledWith('/problems/7', {
        title: '两数之和',
        description: '题干原文',
        allowedLanguages: ['CPP', 'JAVA'],
        timeLimitMs: 2000,
        memoryLimitMb: 512,
        isPublic: true,
      });
    });
    expect(await screen.findByText('题目已更新')).toBeTruthy();
  });
});
