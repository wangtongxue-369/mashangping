import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from '../../auth/useAuth';
import CodingWorkspace, {
  type AssignmentMeta,
  type HistoryAnchor,
  type SubmitTarget,
} from './CodingWorkspace';
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
  status: 'WA',
  score: 0,
  passedCount: 0,
  totalCount: 2,
  timeUsedMs: 5,
  memoryUsedMb: 1,
  isLate: false,
  submittedAt: '2026-09-06T10:00:00',
  samples: [
    {
      pointIndex: 0,
      status: 'WA',
      timeUsedMs: 5,
      memoryUsedMb: 1,
      input: '1 2',
      expectedOutput: '3',
      message: '',
      actualOutput: '程序实际输出X',
    },
  ],
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
  assignmentMeta?: AssignmentMeta;
  /** 覆写 GET 桩（默认 /submissions/my 成功、/submissions/1001 详情）。 */
  customGet?: (url: string) => Promise<unknown>;
}

function renderWorkspace(opts: RenderOpts = {}) {
  const history = opts.history ?? [
    { id: 1001, status: 'AC', score: 20, isLate: false, language: 'C', submittedAt: '2026-09-06T10:00:00' },
  ];
  localStorage.setItem('msp_token', 'test-token');
  mocks.get.mockImplementation((url: string) => {
    if (opts.customGet) return opts.customGet(url);
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
                assignmentMeta={opts.assignmentMeta}
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

/** 提交模板代码时需先过「仍是模板代码」确认（antd 按钮名可能含空格，用正则容差）。 */
async function submitTemplateAndConfirm() {
  fireEvent.click(await screen.findByRole('button', { name: '提交评测' }));
  expect(await screen.findByText(/确定提交吗/)).toBeTruthy();
  fireEvent.click(await screen.findByRole('button', { name: /确定提交/ }));
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

  it('作业模式展示最高分（取历史中最大有效分）', async () => {
    renderWorkspace({
      history: [
        { id: 1, status: 'WA', score: 0, isLate: false, language: 'C', submittedAt: '2026-09-06T09:00:00' },
        { id: 2, status: 'AC', score: 20, isLate: false, language: 'C', submittedAt: '2026-09-06T09:30:00' },
        { id: 3, status: 'PENDING', score: null, isLate: false, language: 'C', submittedAt: '2026-09-06T09:40:00' },
      ],
    });
    expect(await screen.findByText(/我的最高分：20/)).toBeTruthy();
  });

  it('作业模式带满分时展示「最高分 / 满分」', async () => {
    renderWorkspace({
      history: [
        { id: 1, status: 'AC', score: 20, isLate: false, language: 'C', submittedAt: '2026-09-06T09:00:00' },
      ],
      assignmentMeta: { status: 'IN_PROGRESS', dueAt: '2099-01-01T00:00:00', lateDays: 0, fullScore: 20 },
    });
    expect(await screen.findByText(/我的最高分：20 \/ 20/)).toBeTruthy();
  });

  it('练习模式：不展示最高分与得分列，AC 后展示「已通过 N 次」', async () => {
    renderWorkspace({
      history: [
        { id: 1, status: 'WA', score: null, isLate: false, language: 'C', submittedAt: '2026-09-06T09:00:00' },
        { id: 2, status: 'AC', score: null, isLate: false, language: 'C', submittedAt: '2026-09-06T09:30:00' },
        { id: 3, status: 'AC', score: null, isLate: false, language: 'C', submittedAt: '2026-09-06T09:40:00' },
      ],
      submitTarget: { type: 'practice', problemId: 9 },
      historyAnchor: { problemId: 9 },
    });
    expect(await screen.findByText(/已通过 2 次/)).toBeTruthy();
    expect(screen.queryByText(/我的最高分/)).toBeNull();
    openSubmissionsTab();
    expect(screen.queryByRole('columnheader', { name: '得分' })).toBeNull();
  });

  it('详情抽屉：样例点渲染完整输入输出与「你的输出」；隐藏点即使数据含输入输出也不渲染（零泄漏）', async () => {
    renderWorkspace();
    await screen.findByText('两数之和');
    openSubmissionsTab();
    fireEvent.click(await screen.findByText('1001'));
    expect(await screen.findByText(/样例点 1/)).toBeTruthy();
    expect(screen.getByText('1 2')).toBeTruthy(); // 样例输入完整展示
    expect(screen.getByText('3')).toBeTruthy(); // 预期输出完整展示
    expect(screen.getByText('程序实际输出X')).toBeTruthy(); // WA「你的输出」
    expect(screen.getByText(/第 2 个测试点/)).toBeTruthy(); // 隐藏点仅状态/用时内存
    expect(screen.queryByText('HIDDEN-INPUT')).toBeNull(); // 泄漏的隐藏点输入绝不渲染
    expect(screen.queryByText('HIDDEN-OUT')).toBeNull(); // 泄漏的隐藏点输出绝不渲染
    expect(screen.queryByText('HIDDEN-MSG')).toBeNull(); // 泄漏的隐藏点 message 绝不渲染
  });

  it('作业锚提交：模板确认后 POST /submissions 携带 assignmentProblemId 不带 problemId', async () => {
    mocks.post.mockResolvedValue(ok({ id: 1002, status: 'PENDING' }));
    renderWorkspace();
    await submitTemplateAndConfirm();
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
    await submitTemplateAndConfirm();
    await waitFor(() => expect(mocks.post).toHaveBeenCalled());
    const body = mocks.post.mock.calls[0][1] as Record<string, unknown>;
    expect(body.problemId).toBe(9);
    expect(body.assignmentProblemId).toBeUndefined();
    const getCalls = mocks.get.mock.calls.filter((c) => c[0] === '/submissions/my');
    expect(getCalls.length).toBeGreaterThan(0);
    expect(getCalls[0][1].params.problemId).toBe(9);
  });

  it('LATE_WINDOW：提交前出现迟交确认，确认后才 POST', async () => {
    mocks.post.mockResolvedValue(ok({ id: 3001, status: 'PENDING' }));
    renderWorkspace({
      assignmentMeta: { status: 'LATE_WINDOW', dueAt: '2026-09-01T00:00:00', lateDays: 2, fullScore: 20 },
    });
    fireEvent.click(await screen.findByRole('button', { name: '提交评测' }));
    expect(await screen.findByText(/本次提交将标记为/)).toBeTruthy();
    expect(mocks.post).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: /仍然提交/ }));
    await waitFor(() => expect(mocks.post).toHaveBeenCalled());
  });

  it('CLOSED：提交按钮禁用且点击不发起提交', async () => {
    mocks.post.mockResolvedValue(ok({ id: 4001, status: 'PENDING' }));
    renderWorkspace({
      assignmentMeta: { status: 'CLOSED', dueAt: '2026-09-01T00:00:00', lateDays: 0, fullScore: 20 },
    });
    const btn = await screen.findByRole('button', { name: '提交评测' });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText('作业已截止，无法提交')).toBeTruthy();
    fireEvent.click(btn);
    expect(mocks.post).not.toHaveBeenCalled();
  });

  it('历史加载失败显示错误态与重试（不伪装空数据）', async () => {
    renderWorkspace({
      customGet: (url: string) => {
        if (url === '/submissions/my') return Promise.reject({ message: '服务暂不可用', code: 500 });
        return Promise.resolve({ data: { code: 0, message: 'ok', data: null } });
      },
    });
    await screen.findByText('两数之和');
    openSubmissionsTab();
    expect(await screen.findByText('提交记录加载失败，请重试')).toBeTruthy();
  });

  it('切换语言后原语言草稿保留（分语言存编辑内容）', async () => {
    renderWorkspace();
    const monaco = await screen.findByTestId('monaco');
    fireEvent.change(monaco, { target: { value: 'printf("hi")' } });

    // 打开语言下拉并选择目标项：取「下拉浮层内」的选项文本（已选项值也会以文本出现，须区分）。
    const pickOption = async (label: string) => {
      fireEvent.mouseDown(document.querySelector('.ant-select-selector') as HTMLElement);
      const matches = await screen.findAllByText(label);
      // 取「最新渲染的下拉浮层」中的选项（旧的已关闭浮层仍可能残留在 DOM，取最后一个更可靠）
      const inDropdown = matches.filter((el) => el.closest('.ant-select-dropdown')).at(-1);
      expect(inDropdown).toBeTruthy();
      fireEvent.click(inDropdown as HTMLElement);
    };

    await pickOption('C++');
    expect((screen.getByTestId('monaco') as HTMLTextAreaElement).value).toContain('#include <iostream>');
    await pickOption('C');
    expect((screen.getByTestId('monaco') as HTMLTextAreaElement).value).toContain('printf("hi")');
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
