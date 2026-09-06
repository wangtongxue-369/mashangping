import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from '../../auth/useAuth';
import CodingWorkspace from './CodingWorkspace';

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

function renderWorkspace() {
  localStorage.setItem('msp_token', 'test-token');
  mocks.get.mockImplementation((url: string) => {
    if (url === '/submissions/my') {
      return ok([{ id: 1001, status: 'AC', score: 20, isLate: false, language: 'C', submittedAt: '2026-09-06T10:00:00' }]);
    }
    if (url === '/submissions/1001') {
      return ok({
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
        maskedPoints: [{ pointIndex: 1, status: 'WA', timeUsedMs: null, memoryUsedMb: null }],
      });
    }
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
                languages={['C', 'CPP']}
                submitTarget={{ type: 'assignment', assignmentProblemId: 5 }}
                historyAnchor={{ assignmentProblemId: 5 }}
              />
            </MemoryRouter>
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>,
  );
}

describe('CodingWorkspace', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染我的提交历史：状态徽标「通过」与得分', async () => {
    renderWorkspace();
    expect(await screen.findByText('通过')).toBeTruthy();
    expect(screen.getByText('20')).toBeTruthy();
  });

  it('详情抽屉：样例点渲染完整输入输出，隐藏点不展示输入输出', async () => {
    renderWorkspace();
    fireEvent.click(await screen.findByText('1001'));
    expect(await screen.findByText(/样例点 1/)).toBeTruthy();
    expect(screen.getByText('1 2')).toBeTruthy(); // 样例输入
    expect(screen.getByText('3')).toBeTruthy(); // 样例输出
    expect(screen.getByText(/第 2 个测试点/)).toBeTruthy(); // 隐藏点仅状态/用时内存
    expect(screen.queryByText('HIDDEN-IN')).toBeNull(); // 隐藏点不出现任何输入输出
  });

  it('提交走作业锚：POST /submissions 携带 assignmentProblemId', async () => {
    mocks.post.mockResolvedValue(ok({ id: 1002, status: 'PENDING' }));
    renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '提交评测' }));
    await vi.waitFor(() => expect(mocks.post).toHaveBeenCalled());
    const body = mocks.post.mock.calls[0][1];
    expect(body.assignmentProblemId).toBe(5);
    expect(body.problemId).toBeUndefined();
  });

  it('限流/业务错误按 message 提示', async () => {
    mocks.post.mockRejectedValue({ message: '提交过于频繁，请稍后再试' });
    renderWorkspace();
    fireEvent.click(screen.getByRole('button', { name: '提交评测' }));
    expect(await screen.findByText('提交过于频繁，请稍后再试')).toBeTruthy();
  });
});
