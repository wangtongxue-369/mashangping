import { Button, Card, Form, Input, App as AntApp } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

interface LoginForm {
  username: string;
  password: string;
}

/** 教师登录页：成功跳转 /teacher/courses。 */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const { message } = AntApp.useApp();

  async function onFinish(values: LoginForm) {
    try {
      await login(values);
      navigate('/teacher/courses', { replace: true });
    } catch (err) {
      const msg = (err as { message?: string })?.message || '登录失败，请重试';
      message.error(msg);
    }
  }

  return (
    <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f0f2f5' }}>
      <Card title="码上评 · 教师登录" style={{ width: 380 }}>
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