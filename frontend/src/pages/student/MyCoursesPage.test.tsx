import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import MyCoursesPage from './MyCoursesPage';

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }));

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return { ...actual, client: mocks };
});

function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MyCoursesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('MyCoursesPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染我的课程卡片：课程名 + 学期', async () => {
    mocks.get.mockResolvedValue(ok([{ courseId: 1, name: '数据结构', term: '2026秋' }]));
    renderPage();
    expect(await screen.findByText('数据结构')).toBeTruthy();
    expect(screen.getByText(/2026秋/)).toBeTruthy();
  });

  it('无课程时展示空态提示与去自由练习入口', async () => {
    mocks.get.mockResolvedValue(ok([]));
    renderPage();
    expect(await screen.findByText(/你还没有加入任何课程/)).toBeTruthy();
    expect(screen.getByText('去自由练习')).toBeTruthy();
  });
});
