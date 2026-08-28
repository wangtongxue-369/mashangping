import { vi } from 'vitest';

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