import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PracticePage from './PracticePage';

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
        <PracticePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PracticePage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('渲染公开题库列表：题目标题/语言标签', async () => {
    mocks.get.mockResolvedValue(
      ok({
        records: [
          { id: 3, title: 'A+B 问题', languages: ['C', 'JAVA'], timeLimitMs: 1000, memoryLimitMb: 256 },
        ],
        total: 1,
        size: 20,
        current: 1,
      }),
    );
    renderPage();
    expect(await screen.findByText('A+B 问题')).toBeTruthy();
    expect(screen.getByText('Java')).toBeTruthy();
  });
});
