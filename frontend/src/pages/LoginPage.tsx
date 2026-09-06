import { Button, Card, Form, Input, App as AntApp } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth, type LoginRequest } from '../auth/useAuth';

/**
 * 登录页：成功后按角色分流（STUDENT → /student/courses，其余 → /teacher/courses）。
 * ADMIN 端尚未建设：提示后清会话留在登录页，避免进入教师端踩 403 死胡同。
 */
export default function LoginPage() {
  const { login, logout } = useAuth();
  const navigate = useNavigate();
  const { message } = AntApp.useApp();

  async function onFinish(values: LoginRequest) {
    try {
      const user = await login(values);
      if (user.role === 'ADMIN') {
        message.warning('管理员功能建设中，请使用教师或学生账号登录');
        logout();
        return;
      }
      navigate(user.role === 'STUDENT' ? '/student/courses' : '/teacher/courses', { replace: true });
    } catch (err) {
      const msg = (err as { message?: string })?.message || '登录失败，请重试';
      message.error(msg);
    }
  }

  return (
    <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#f0f2f5' }}>
      <Card title="码上评 · 登录" style={{ width: 380 }}>
        <Form<LoginRequest> layout="vertical" onFinish={onFinish}>
          <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" block>
              登录
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center' }}>
            <Button type="link" style={{ padding: 0 }} onClick={() => navigate('/register')}>
              没有账号？注册学生账号
            </Button>
          </div>
        </Form>
      </Card>
    </div>
  );
}
