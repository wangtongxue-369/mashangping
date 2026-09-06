import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import RegisterPage from './RegisterPage';
import { AuthProvider } from '../auth/useAuth';

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

function ok(data: unknown) {
  return Promise.resolve({ data: { code: 0, message: 'ok', data } });
}

function renderRegister() {
  return render(
    <ConfigProvider locale={zhCN}>
      <AntApp>
        <AuthProvider>
          <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }} initialEntries={['/register']}>
            <Routes>
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/student/courses" element={<div>学生课程页</div>} />
              <Route path="/login" element={<div>登录页</div>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </AntApp>
    </ConfigProvider>,
  );
}

function fillField(placeholder: string, value: string) {
  fireEvent.change(screen.getByPlaceholderText(placeholder), { target: { value } });
}

describe('RegisterPage', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    mocks.post.mockResolvedValue(ok(null));
  });

  it('发送验证码请求 /auth/register/code 并进入倒计时', async () => {
    renderRegister();
    fillField('学校邮箱', 'stu@stu.example.edu.cn');
    fireEvent.click(screen.getByRole('button', { name: /发送验证码/ }));

    await waitFor(() => {
      expect(mocks.post).toHaveBeenCalledWith('/auth/register/code', {
        email: 'stu@stu.example.edu.cn',
      });
    });
    expect(await screen.findByText(/60s 后重发/)).toBeTruthy();
  });

  it('注册成功即自动登录并跳转学生课程页', async () => {
    mocks.post.mockReset();
    mocks.post
      .mockResolvedValueOnce(ok(null)) // 发码
      .mockResolvedValueOnce(
        ok({ token: 'reg-token', user: { id: 1, username: '20260001', realName: '新同学', role: 'STUDENT' } }),
      );
    renderRegister();

    fillField('学校邮箱', 'stu@stu.example.edu.cn');
    fireEvent.click(screen.getByRole('button', { name: /发送验证码/ }));
    await screen.findByText(/60s 后重发/);

    fillField('6 位数字验证码', '123456');
    fillField('学号（同时是登录用户名）', '20260001');
    fillField('真实姓名', '新同学');
    fillField('6~64 位', 'secret123');
    fillField('再次输入密码', 'secret123');
    fireEvent.click(screen.getByRole('button', { name: /注册并登录/ }));

    expect(await screen.findByText('学生课程页')).toBeTruthy();
    expect(mocks.post).toHaveBeenCalledWith('/auth/register', {
      email: 'stu@stu.example.edu.cn',
      code: '123456',
      studentNo: '20260001',
      realName: '新同学',
      password: 'secret123',
    });
    expect(localStorage.getItem('msp_token')).toBe('reg-token');
  });

  it('后端拒绝时展示业务文案且不跳转', async () => {
    mocks.post.mockReset();
    mocks.post
      .mockResolvedValueOnce(ok(null)) // 发码
      .mockImplementationOnce(() => Promise.reject({ code: 40013, message: '该学号已被占用', data: null }));
    renderRegister();

    fillField('学校邮箱', 'stu@stu.example.edu.cn');
    fireEvent.click(screen.getByRole('button', { name: /发送验证码/ }));
    await screen.findByText(/60s 后重发/);

    fillField('6 位数字验证码', '123456');
    fillField('学号（同时是登录用户名）', '20260001');
    fillField('真实姓名', '新同学');
    fillField('6~64 位', 'secret123');
    fillField('再次输入密码', 'secret123');
    fireEvent.click(screen.getByRole('button', { name: /注册并登录/ }));

    expect(await screen.findByText('该学号已被占用')).toBeTruthy();
    expect(screen.queryByText('学生课程页')).toBeNull();
    expect(localStorage.getItem('msp_token')).toBeNull();
  });

  it('密码与确认密码不一致时提示且不提交', async () => {
    renderRegister();
    fillField('学校邮箱', 'stu@stu.example.edu.cn');
    fillField('6 位数字验证码', '123456');
    fillField('学号（同时是登录用户名）', '20260001');
    fillField('真实姓名', '新同学');
    fillField('6~64 位', 'secret123');
    fillField('再次输入密码', 'secret456');
    fireEvent.click(screen.getByRole('button', { name: /注册并登录/ }));

    expect(await screen.findByText('两次输入的密码不一致')).toBeTruthy();
    expect(mocks.post).not.toHaveBeenCalledWith(
      '/auth/register',
      expect.objectContaining({ email: 'stu@stu.example.edu.cn' }),
    );
  });

  it('提供返回登录入口', async () => {
    renderRegister();
    fireEvent.click(screen.getByRole('button', { name: /返回登录/ }));
    expect(await screen.findByText('登录页')).toBeTruthy();
  });
});
