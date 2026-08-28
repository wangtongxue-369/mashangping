import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import ProblemDetailPage from './ProblemDetailPage';
import ProtectedRoute from '../../layout/ProtectedRoute';
import { AuthProvider } from '../../auth/useAuth';
import type { ProblemDetailView } from '../../api/types';

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

const DETAIL: ProblemDetailView = {
  id: 7,
  title: '两数之和',
  description: '# 题干\n\n给定整数数组，求两数之和',
  languages: ['CPP', 'JAVA'],
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  isPublic: false,
  testCases: [
    { id: 1, input: '4\n1 2 3 4', expectedOutput: '3 7', isSample: true },
    { id: 2, input: '10 20\n-1 1', expectedOutput: '30 0', isSample: false },
  ],
  createdAt: '2026-08-27T10:30:00',
  updatedAt: '2026-08-27T12:00:00',
};

function renderDetailPage(noToken = false) {
  if (!noToken) localStorage.setItem('msp_token', 'test-token');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <MemoryRouter
              future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
              initialEntries={['/teacher/problems/7']}
            >
              <Routes>
                <Route path="/login" element={<div>登录页</div>} />
                <Route
                  path="/teacher/problems/:problemId"
                  element={
                    <ProtectedRoute>
                      <ProblemDetailPage />
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

describe('ProblemDetailPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染题干 Markdown（标题/加粗为真实 DOM）+ 隐藏点标注 + 测试点列表（样例/隐藏 tag）', async () => {
    mocks.get.mockResolvedValue(ok(DETAIL));
    renderDetailPage();

    // 题干经 marked 渲染为真实标题节点，而非原文「# 题干」
    expect(await screen.findByRole('heading', { level: 1, name: '题干' })).toBeTruthy();
    // 标题旁：私有 tag + 含隐藏点标注（isSample=false 计 1 个）
    expect(screen.getByText('私有')).toBeTruthy();
    expect(screen.getByText('含隐藏点 1 个')).toBeTruthy();
    // 测试点表格：类型 tag 与内容摘要
    expect(screen.getByText('样例')).toBeTruthy();
    expect(screen.getByText('隐藏')).toBeTruthy();
    expect(screen.getByText('4 1 2 3 4')).toBeTruthy();
    expect(screen.getByText('10 20 -1 1')).toBeTruthy();
    expect(screen.getByText('3 7')).toBeTruthy();
  });

  it('新增样例测试点：提交后按 TestCaseUpsertRequest 契约 POST 并刷新详情', async () => {
    mocks.get.mockResolvedValue(ok(DETAIL));
    mocks.post.mockResolvedValue(ok({ id: 3 }));
    renderDetailPage();

    // 「新增测试点」在详情加载完成前置灰，等按钮可用再点；图标 aria 参与可访问名，用正则。
    const addBtn = (await screen.findByRole('button', { name: /新增测试点/ })) as HTMLButtonElement;
    await waitFor(() => expect(addBtn.disabled).toBe(false));
    fireEvent.click(addBtn);
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeTruthy();
    fireEvent.change(screen.getByLabelText('输入'), { target: { value: '5\n1 2 3 4 5' } });
    fireEvent.change(screen.getByLabelText('期望输出'), { target: { value: '3 7 12' } });
    // antd 按钮对两字文案自动插空格，可访问名为「添 加」，用正则匹配。
    fireEvent.click(screen.getByRole('button', { name: /添\s*加/ }));

    await waitFor(() => {
      expect(mocks.post).toHaveBeenCalledWith('/problems/7/test-cases', {
        input: '5\n1 2 3 4 5',
        expectedOutput: '3 7 12',
        isSample: true,
      });
    });
    expect(await screen.findByText('测试点已添加')).toBeTruthy();
    await waitFor(() => {
      expect(mocks.get).toHaveBeenCalledTimes(2); // 初次加载 + invalidate 后重取
    });
  });
});
