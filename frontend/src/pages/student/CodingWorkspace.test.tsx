import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from '../../auth/useAuth';
import CodingWorkspace, { type HistoryAnchor, type SubmitTarget } from './CodingWorkspace';
import type { StudentSample, SubmissionSummary } from '../../api/studentTypes';

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }));

vi.mock('@monaco-editor/react', () => ({
  default: (props: { value?: string; onChange?: (v: string) => void }) => (
    <textarea data-testid="monaco" value={props.value ?? ''} onChange={(e) => props.onChange?.(e.target.value)} />
  ),
}));

vi.mock('../../api/ws', () => ({ connect: () => () => {} }));

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, client: mocks };
});

function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

/** 作业锚详情：样例点完整 + 隐藏点（含被塞入的非法键 input/expectedOutput，用于验证前端不渲染）。 */
const detailWithLeakedMasked = {
  id: 1001,
  problemId: 9,
  language: 'C',
  code: 'int main(){}',
  status: 'AC',
  score: 20,
  passedCount: 2,
  totalCount: 3,
  timeUsedMs: 5,
  memoryUsedMb: 1,
  isLate: false,
  submittedAt: '2026-09-06T10:00:00',
  samples: [{ pointIndex: 0, status: 'AC', timeUsedMs: 5, memoryUsedMb: 1, input: '1 2', expectedOutput: '3', message: '' }],
  // 类型上 MaskedPoint 只有四字段；此处用 any 塞入泄漏内容，验证零泄漏断言非空转。
  maskedPoints: [
    { pointIndex: 1, status: 'WA', timeUsedMs: null, memoryUsedMb: null, input: 'HIDDEN-INPUT', expectedOutput: 'HIDDEN-OUT', message: 'HIDDEN-MSG' },
  ],
};

interface RenderOpts {
  history?: SubmissionSummary[];
  samples?: StudentSample[];
  submitTarget?: SubmitTarget;
  historyAnchor?: HistoryAnchor;
}

function renderWorkspace(opts: RenderOpts = {}) {
  const history = opts.history ?? [
    { id: 1001, status: 'AC', score: 20, isLate: false, language: 'C', submittedAt: '2026-09-06T10:00:00' },
  ];
  localStorage.setItem('msp_token', 'test-token');
  mocks.get.mockImplementation((url: string) => {
    if (url === '/submissions/my') return ok(history);
    if (url === '/submissions/1001') return ok(detailWithLeakedMasked);
    return Promise.reject(new Error(`unhandled ${url}`));
  });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <MemoryRouter>
              <CodingWorkspace
                title="两数之和"
                description="输入两个整数 a b，输出 a+b。"
                samples={opts.samples ?? []}
                languages={['C', 'CPP']}
                timeLimitMs={1000}
                memoryLimitMb={256}
                submitTarget={opts.submitTarget ?? { type: 'assignment', assignmentProblemId: 5 }}
                historyAnchor={opts.historyAnchor ?? { assignmentProblemId: 5 }}
              />
            </MemoryRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>,
  );
}

/** 切到「提交记录」Tab（antd 惰性渲染，不激活则历史表格不在 DOM）。 */
function openSubmissionsTab() {
  fireEvent.click(screen.getByRole('tab', { name: /提交记录/ }));
}

describe('CodingWorkspace', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('题目描述区默认展示题干与样例输入输出', async () => {
    renderWorkspace({ samples: [{ input: '1 2', output: '3' }] });
    expect(await screen.findByText('两数之和')).toBeTruthy();
    expect(screen.getByText(/输入两个整数 a b/)).toBeTruthy();
    expect(screen.getByText('1 2')).toBeTruthy(); // 样例输入
    expect(screen.getByText('3')).toBeTruthy(); // 样例输出
  });

  it('提交记录 Tab：渲染历史行与状态徽标「通过」', async () => {
    renderWorkspace();
    await screen.findByText('两数之和');
    openSubmissionsTab();
    expect(await screen.findByText('通过')).toBeTruthy();
  });

  it('展示我的最高分（取历史中最大有效分）', async () => {
    renderWorkspace({
      history: [
        { id: 1, status: 'WA', score: 0, isLate: false, language: 'C', submittedAt: '2026-09-06T09:00:00' },
        { id: 2, status: 'AC', score: 20, isLate: false, language: 'C', submittedAt: '2026-09-06T09:30:00' },
        { id: 3, status: 'PENDING', score: null, isLate: false, language: 'C', submittedAt: '2026-09-06T09:40:00' },
      ],
    });
    expect(await screen.findByText(/我的最高分：20/)).toBeTruthy();
  });

  it('详情抽屉：样例点渲染完整输入输出，隐藏点即使数据含输入输出也不渲染（零泄漏）', async () => {
    renderWorkspace();
    await screen.findByText('两数之和');
    openSubmissionsTab();
    fireEvent.click(await screen.findByText('1001'));
    expect(await screen.findByText(/样例点 1/)).toBeTruthy();
    expect(screen.getByText('1 2')).toBeTruthy(); // 样例输入完整展示
    expect(screen.getByText('3')).toBeTruthy(); // 样例输出完整展示
    expect(screen.getByText(/第 2 个测试点/)).toBeTruthy(); // 隐藏点仅状态/用时内存
    expect(screen.queryByText('HIDDEN-INPUT')).toBeNull(); // 泄漏的隐藏点输入绝不渲染
    expect(screen.queryByText('HIDDEN-OUT')).toBeNull(); // 泄漏的隐藏点输出绝不渲染
    expect(screen.queryByText('HIDDEN-MSG')).toBeNull(); // 泄漏的隐藏点 message 绝不渲染
  });

  it('作业锚提交：POST /submissions 携带 assignmentProblemId 不带 problemId', async () => {
    mocks.post.mockResolvedValue(ok({ id: 1002, status: 'PENDING' }));
    renderWorkspace();
    fireEvent.click(await screen.findByRole('button', { name: '提交评测' }));
    await waitFor(() => expect(mocks.post).toHaveBeenCalled());
    const body = mocks.post.mock.calls[0][1] as Record<string, unknown>;
    expect(body.assignmentProblemId).toBe(5);
    expect(body.problemId).toBeUndefined();
  });

  it('练习锚提交：POST /submissions 携带 problemId 不带 assignmentProblemId，历史参数走 problemId', async () => {
    mocks.post.mockResolvedValue(ok({ id: 2001, status: 'PENDING' }));
    renderWorkspace({
      submitTarget: { type: 'practice', problemId: 9 },
      historyAnchor: { problemId: 9 },
    });
    fireEvent.click(await screen.findByRole('button', { name: '提交评测' }));
    await waitFor(() => expect(mocks.post).toHaveBeenCalled());
    const body = mocks.post.mock.calls[0][1] as Record<string, unknown>;
    expect(body.problemId).toBe(9);
    expect(body.assignmentProblemId).toBeUndefined();
    const getCalls = mocks.get.mock.calls.filter((c) => c[0] === '/submissions/my');
    expect(getCalls.length).toBeGreaterThan(0);
    expect(getCalls[0][1].params.problemId).toBe(9);
  });

  it('限流/业务错误按 message 提示，且不因提交失败清空代码', async () => {
    mocks.post.mockRejectedValue({ message: '提交过于频繁，请稍后再试' });
    renderWorkspace();
    const monaco = await screen.findByTestId('monaco');
    fireEvent.change(monaco, { target: { value: 'int main(){}' } });
    fireEvent.click(screen.getByRole('button', { name: '提交评测' }));
    expect(await screen.findByText('提交过于频繁，请稍后再试')).toBeTruthy();
    expect((screen.getByTestId('monaco') as HTMLTextAreaElement).value).toBe('int main(){}');
  });
});
