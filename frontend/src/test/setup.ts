import { vi } from 'vitest';

// 本环境 jsdom 未挂载 localStorage（window.localStorage 亦为 undefined），
// 而大量用例（AuthProvider/守卫/拦截器）依赖 token 的读写在 localStorage。
// 提供进程内最小实现补全 Storage 接口，供全部用例共享。
function createMemoryStorage(): Storage {
  let store: Record<string, string> = {};
  return {
    get length() {
      return Object.keys(store).length;
    },
    clear: () => {
      store = {};
    },
    getItem: (key: string) => (key in store ? store[key] : null),
    key: (index: number) => Object.keys(store)[index] ?? null,
    removeItem: (key: string) => {
      delete store[key];
    },
    setItem: (key: string, value: string) => {
      store[key] = String(value);
    },
  };
}

const memoryStorage = createMemoryStorage();
Object.defineProperty(window, 'localStorage', { value: memoryStorage, configurable: true });
Object.defineProperty(globalThis, 'localStorage', { value: memoryStorage, configurable: true });

// jsdom 未实现 window.matchMedia —— antd 响应式组件依赖，测试须桩掉。
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// jsdom 未实现伪元素 getComputedStyle —— rc-table 测滚动条宽度会查询
// '::-webkit-scrollbar' 伪元素，直接 "Not implemented" 报错刷屏；退化为元素自身样式。
const nativeGetComputedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = (elt: Element, pseudoElt?: string | null) =>
  nativeGetComputedStyle(elt, pseudoElt ? undefined : pseudoElt);