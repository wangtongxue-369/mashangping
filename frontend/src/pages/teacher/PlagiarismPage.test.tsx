import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import PlagiarismPage, { markSharedLines } from './PlagiarismPage';

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }));

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, client: mocks };
});

function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

const studentA = { studentId: 1, studentNo: 'P001', realName: '甲学生', language: 'C', score: 100, ac: true, submissionId: 11 };
const studentB = { studentId: 2, studentNo: 'P002', realName: '乙学生', language: 'C', score: 100, ac: true, submissionId: 22 };
const studentC = { studentId: 3, studentNo: 'P003', realName: '丙学生', language: 'C', score: 100, ac: true, submissionId: 33 };

function mockReport() {
  mocks.get.mockImplementation((url: string) => {
    if (url.includes('/compare')) {
      return ok({
        a: studentA,
        b: studentB,
        codeA: 'int main(){int s=0;printf("%d",s);return 0;}',
        codeB: 'int main(){int s=0;printf("%d",s);return 0;}',
      });
    }
    if (url.includes('/plagiarism/problems/9')) {
      return ok({
        assignmentId: 3,
        problemId: 9,
        problemTitle: '求和题',
        highThreshold: 0.8,
        participants: 3,
        items: [
          { a: studentA, b: studentB, similarity: 0.95, flag: 'HIGH' },
          { a: studentA, b: studentC, similarity: 0.52, flag: 'MID' },
        ],
      });
    }
    return Promise.reject(new Error(`unhandled ${url}`));
  });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/teacher/assignments/3/plagiarism/9']}>
            <Routes>
              <Route path="/teacher/assignments/:assignmentId/plagiarism/:problemId" element={<PlagiarismPage />} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>,
  );
}

describe('PlagiarismPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染疑似对：学生名/相似度/高疑似与关注标签', async () => {
    mockReport();
    renderPage();
    expect((await screen.findAllByText('甲学生')).length).toBeGreaterThan(0);
    expect(screen.getByText('乙学生')).toBeTruthy();
    expect(screen.getByText('高疑似')).toBeTruthy();
    expect(screen.getByText('关注')).toBeTruthy();
    expect(screen.getByText(/95\.0%/)).toBeTruthy();
    expect(screen.getByText(/参与学生 3 人/)).toBeTruthy();
  });

  it('点击疑似对打开对比抽屉并展示双方代码', async () => {
    mockReport();
    renderPage();
    const names = await screen.findAllByText('甲学生');
    fireEvent.click(names[0]);
    expect(await screen.findByText(/学生 A 代码/)).toBeTruthy();
    expect(await screen.findByText(/学生 B 代码/)).toBeTruthy();
    await waitFor(() => expect(screen.getAllByText(/int s=0/).length).toBeGreaterThan(0));
  });

  it('空报告展示空态文案', async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url.includes('/plagiarism/problems/9')) {
        return ok({ assignmentId: 3, problemId: 9, problemTitle: '求和题', highThreshold: 0.8, participants: 0, items: [] });
      }
      return Promise.reject(new Error(`unhandled ${url}`));
    });
    renderPage();
    expect(await screen.findByText(/未发现相似度 ≥ 50%/)).toBeTruthy();
  });
});

describe('markSharedLines', () => {
  it('完全相同的行两侧都标记为共享', () => {
    const a = 'int a = 1;\nint b = 2;\n';
    const b = 'int b = 2;\nint c = 3;\n';
    const { aShared, bShared } = markSharedLines(a, b);
    expect(aShared.has(1)).toBe(true); // 'int b = 2;' 在两侧都出现
    expect(bShared.has(0)).toBe(true);
    expect(aShared.has(0)).toBe(false); // 'int a = 1;' 不出现在 b
  });
});
