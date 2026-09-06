import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import LoginPage from './LoginPage';
import { AuthProvider } from '../auth/useAuth';

// 只替换 axios 实例（对齐 CoursesPage.test 的风格），useAuth/AuthProvider 走真实实现。
const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return { ...actual, client: mocks };
});

/** 后端统一信封的 axios 响应形态。 */
function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

/** 渲染登录页 + 分流目标页 + 注册页；login 依 role 写用户快照。 */
function renderLogin(user: { username: string; realName: string; role: string }) {
  mocks.post.mockResolvedValue(ok({ token: 'test-token', user }));
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <AuthProvider>
          <MemoryRouter
            future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
            initialEntries={['/login']}
          >
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/student/courses" element={<div>学生课程页</div>} />
              <Route path="/teacher/courses" element={<div>教师课程页</div>} />
              <Route path="/register" element={<div>注册页</div>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </AntApp>
    </ConfigProvider>,
  );
}

/** 填用户名/密码并点击登录。 */
function submitLogin() {
  fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'u' } });
  fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'secret' } });
  // antd 对两个中文字符的按钮会自动在字间插入空格（autoInsertSpaceInButton），
  // 用正则可容忍「登 录 / 登录」两种形态。
  fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }));
}

describe('LoginPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    window.history.pushState({}, '', '/login');
  });

  it('学生账号登录成功后跳转 /student/courses', async () => {
    renderLogin({ username: 'stu', realName: '学生', role: 'STUDENT' });
    submitLogin();
    expect(await screen.findByText('学生课程页')).toBeTruthy();
  });

  it('教师账号登录成功后跳转 /teacher/courses', async () => {
    renderLogin({ username: 'tea', realName: '教师', role: 'TEACHER' });
    submitLogin();
    expect(await screen.findByText('教师课程页')).toBeTruthy();
  });

  it('ADMIN 登录提示建设中并清除会话不跳转', async () => {
    renderLogin({ username: 'admin', realName: '管理员', role: 'ADMIN' });
    submitLogin();
    expect(await screen.findByText(/管理员功能建设中/)).toBeTruthy();
    // 死胡同提示后应原地清会话，不进入教师空端
    expect(localStorage.getItem('msp_token')).toBeNull();
    expect(screen.queryByText('教师课程页')).toBeNull();
  });

  it('无账号时提供注册入口并可进入注册页', async () => {
    renderLogin({ username: 'stu', realName: '学生', role: 'STUDENT' });
    fireEvent.click(screen.getByRole('button', { name: /注册学生账号/ }));
    expect(await screen.findByText('注册页')).toBeTruthy();
  });
});
