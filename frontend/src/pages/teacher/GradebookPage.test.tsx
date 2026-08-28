import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import GradebookPage from './GradebookPage';
import ProtectedRoute from '../../layout/ProtectedRoute';
import { AuthProvider } from '../../auth/useAuth';
import type { GradebookView } from '../../api/gradebookTypes';

// 只替换 axios 实例，extractApiMessage 等纯函数保留真实实现。
const mocks = vi.hoisted(() => ({
  get: vi.fn(),
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
 * 矩阵 fixture：张三有满分格 20；李四有 0 分格（bestScore=0 必须显示 0）与 10 分格；
 * 王五（学生C）无任何 cell（未提交）→ 两格均显 —，且 totals 无其条目 → 总分显 0。
 */
const VIEW: GradebookView = {
  assignmentId: 5,
  assignmentTitle: '第一次作业',
  students: [
    { studentId: 1, studentNo: '2024001', realName: '张三' },
    { studentId: 2, studentNo: '2024002', realName: '李四' },
    { studentId: 3, studentNo: '2024003', realName: '王五' },
  ],
  problems: [
    { assignmentProblemId: 11, problemId: 101, title: '两数之和', score: 20, sortOrder: 1 },
    { assignmentProblemId: 12, problemId: 102, title: '回文数', score: 30, sortOrder: 2 },
  ],
  cells: [
    { studentId: 1, assignmentProblemId: 11, bestScore: 20 },
    { studentId: 2, assignmentProblemId: 11, bestScore: 0 },
    { studentId: 2, assignmentProblemId: 12, bestScore: 10 },
  ],
  totals: [
    { studentId: 1, total: 20 },
    { studentId: 2, total: 10 },
  ],
};

/** Task 2 Harness 模式：ConfigProvider zh_CN + AntApp（message 上下文）+ 独立 QueryClient + 路由。 */
function renderGradebook() {
  localStorage.setItem('msp_token', 'test-token');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/teacher/assignments/5/gradebook']}>
              <Routes>
                <Route path="/login" element={<div>登录页</div>} />
                <Route
                  path="/teacher/assignments/:assignmentId/gradebook"
                  element={
                    <ProtectedRoute>
                      <GradebookPage />
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

describe('GradebookPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    mocks.get.mockResolvedValue(ok(VIEW));
  });

  /** scroll 模式下 antd 会渲染一份 aria-hidden 量宽副本，取首个匹配定位该生所在行。 */
  function rowOf(studentNo: string): HTMLTableRowElement {
    return screen.getAllByText(studentNo)[0].closest('tr') as HTMLTableRowElement;
  }

  it('矩阵渲染：20 格显 20、0 格显 0、无 cell 格显 —、总分列取 totals（无条目兜底 0）', async () => {
    renderGradebook();

    // 卡片标题为「成绩册 · {assignmentTitle}」整段文本节点，用子串匹配断言标题渲染
    expect(await screen.findByText(/第一次作业/)).toBeTruthy();
    // 动态列表头 = 题名(分值)，固定首列学号/姓名、末列总分（量宽副本使表头出现两次）
    expect(screen.getAllByText('两数之和(20)').length).toBeGreaterThan(0);
    expect(screen.getAllByText('回文数(30)').length).toBeGreaterThan(0);
    // 按契约请求成绩册端点
    expect(mocks.get).toHaveBeenCalledWith('/assignments/5/gradebook');
    // 顶部返回作业详情链接按 Task 5 锁定的路径渲染
    expect(screen.getByRole('link', { name: /返回作业详情/ }).getAttribute('href')).toBe(
      '/teacher/assignments/5',
    );

    // 张三：两数之和格 20 + 总分 20；回文数未提交显 —
    const zhang = rowOf('2024001');
    expect(within(zhang).getAllByText('20')).toHaveLength(2);
    expect(within(zhang).getByText('—')).toBeTruthy();

    // 李四：0 分格必须显示 0（而非 — 或空），另一格 10 + 总分为 totals 的 10
    const li = rowOf('2024002');
    expect(within(li).getByText('0')).toBeTruthy();
    expect(within(li).getAllByText('10')).toHaveLength(2);
    expect(within(li).queryByText('—')).toBeNull();

    // 王五（学生C）：无任何 cell → 两格均显 —；totals 无条目 → 总分兜底 0
    const wang = rowOf('2024003');
    expect(within(wang).getAllByText('—')).toHaveLength(2);
    expect(within(wang).getByText('0')).toBeTruthy();
  });

  it('导出 CSV：client blob 下载（gradebook-{id}.csv）并提示成功，而非 window.open', async () => {
    // jsdom 未实现 createObjectURL 与 <a download> 导航——桩掉并断言调用形状。
    const createObjectURL = vi.fn(() => 'blob:mock-url');
    const revokeObjectURL = vi.fn();
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = revokeObjectURL;
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    const blob = new Blob(['学号,姓名\n2024001,张三\n'], { type: 'text/csv' });
    mocks.get.mockImplementation((url: string) => {
      if (url === '/assignments/5/gradebook') return ok(VIEW);
      if (url === '/assignments/5/gradebook/csv') return Promise.resolve({ data: blob });
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });
    renderGradebook();
    fireEvent.click(await screen.findByRole('button', { name: /导出 CSV/ }));

    await waitFor(() => {
      expect(mocks.get).toHaveBeenCalledWith('/assignments/5/gradebook/csv', {
        responseType: 'blob',
      });
    });
    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(anchorClick).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
    expect(await screen.findByText('成绩册 CSV 已导出')).toBeTruthy();
  });

  it('CSV 业务错误（HTTP200+JSON 信封伪装成 blob）不触发下载并提示业务文案', async () => {
    // 后端对 BizException 一律 200：responseType blob 时错误 JSON 会伪装成 CSV 响应，
    // 页面必须识别 json 类型错误体、提示业务 message，且不得触发下载。
    const createObjectURL = vi.fn(() => 'blob:mock-url');
    URL.createObjectURL = createObjectURL;
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    const errText = JSON.stringify({ code: 40400, message: '作业不存在' });
    const errBlob = new Blob([errText], { type: 'application/json' });
    // jsdom 的 Blob 未实现 text()——按已知内容补上，组件侧 JSON 解析仍走真实实现。
    if (!errBlob.text) {
      Object.defineProperty(errBlob, 'text', { value: () => Promise.resolve(errText) });
    }
    mocks.get.mockImplementation((url: string) => {
      if (url === '/assignments/5/gradebook') return ok(VIEW);
      if (url === '/assignments/5/gradebook/csv') return Promise.resolve({ data: errBlob });
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });
    renderGradebook();
    fireEvent.click(await screen.findByRole('button', { name: /导出 CSV/ }));

    expect(await screen.findByText('作业不存在')).toBeTruthy();
    expect(createObjectURL).not.toHaveBeenCalled();
    expect(anchorClick).not.toHaveBeenCalled();
    expect(screen.queryByText('成绩册 CSV 已导出')).toBeNull();
  });
});
