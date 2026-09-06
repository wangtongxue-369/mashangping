import { Button, Card, Form, Input, App as AntApp } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth, type AuthUser } from '../auth/useAuth';

interface LoginForm {
  username: string;
  password: string;
}

/** 从本地已存储的用户快照读取角色，供登录成功后分流跳转；异常时按非 STUDENT 兜底。 */
function storedUserRole(): string | undefined {
  const raw = localStorage.getItem('msp_user');
  if (!raw) return undefined;
  try {
    return (JSON.parse(raw) as AuthUser).role;
  } catch {
    return undefined;
  }
}

/** 登录页：成功后按角色分流（STUDENT → /student/courses，其余 → /teacher/courses）。 */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const { message } = AntApp.useApp();

  async function onFinish(values: LoginForm) {
    try {
      await login(values);
      navigate(storedUserRole() === 'STUDENT' ? '/student/courses' : '/teacher/courses', { replace: true });
    } catch (err) {
      const msg = (err as { message?: string })?.message || '登录失败，请重试';
      message.error(msg);
    }
  }

  return (
    <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f0f2f5' }}>
      <Card title="码上评 · 登录" style={{ width: 380 }}>
        <Form<LoginForm> layout="vertical" onFinish={onFinish}>
          <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}