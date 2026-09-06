import { useEffect, useRef, useState } from 'react';
import { Alert, App as AntApp, Button, Card, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth, type RegisterRequest } from '../auth/useAuth';
import { sendRegisterCode } from '../api/register';

const RESEND_SECONDS = 60;

interface RegisterForm {
  email: string;
  code: string;
  studentNo: string;
  realName: string;
  password: string;
  confirm: string;
}

/**
 * 学生自助注册页：邮箱验证码 → 学号/姓名/密码，注册即登录（后端直接签发会话）。
 * 由老师 Excel 导入过学号的同学，注册成功后自动激活并加入对应课程。
 */
export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const [form] = Form.useForm<RegisterForm>();
  const [sending, setSending] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const timerRef = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearInterval(timerRef.current), []);

  const EMAIL_RULE = /^\S+@\S+\.\S+$/;

  async function onSendCode() {
    const email = form.getFieldValue('email');
    if (!email || !EMAIL_RULE.test(email)) {
      message.error('请先填写格式正确的学校邮箱');
      return;
    }
    setSending(true);
    try {
      await sendRegisterCode(email);
      message.success('验证码已发送，请查收邮箱（留意垃圾箱）');
      setCountdown(RESEND_SECONDS);
      timerRef.current = window.setInterval(() => {
        setCountdown((c) => {
          if (c <= 1) {
            window.clearInterval(timerRef.current);
            return 0;
          }
          return c - 1;
        });
      }, 1000);
    } catch (err) {
      const msg = (err as { message?: string })?.message || '验证码发送失败，请稍后重试';
      message.error(msg);
    } finally {
      setSending(false);
    }
  }

  async function onFinish(values: RegisterForm) {
    try {
      const payload: RegisterRequest = {
        email: values.email,
        code: values.code,
        studentNo: values.studentNo,
        realName: values.realName,
        password: values.password,
      };
      await register(payload);
      message.success('注册成功，已自动登录');
      navigate('/student/courses', { replace: true });
    } catch (err) {
      const msg = (err as { message?: string })?.message || '注册失败，请重试';
      message.error(msg);
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
        padding: '24px 0',
      }}
    >
      <Card title="码上评 · 学生注册" style={{ width: 420 }}>
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="使用学校邮箱接收验证码；由老师导入过学号的同学，注册成功后会自动激活并加入对应课程。"
        />
        <Form<RegisterForm> form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            label="邮箱"
            name="email"
            rules={[
              { required: true, message: '请输入邮箱' },
              { type: 'email', message: '邮箱格式不正确' },
            ]}
          >
            <Input placeholder="学校邮箱" autoComplete="email" />
          </Form.Item>
          <Form.Item
            label="验证码"
            name="code"
            rules={[
              { required: true, message: '请输入验证码' },
              { pattern: /^\d{6}$/, message: '验证码须为6位数字' },
            ]}
            extra="验证码 15 分钟内有效，60 秒后可重新发送"
          >
            <Input
              placeholder="6 位数字验证码"
              maxLength={6}
              suffix={
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  disabled={sending || countdown > 0}
                  onClick={onSendCode}
                >
                  {countdown > 0 ? `${countdown}s 后重发` : '发送验证码'}
                </Button>
              }
            />
          </Form.Item>
          <Form.Item
            label="学号"
            name="studentNo"
            rules={[
              { required: true, message: '请输入学号' },
              { pattern: /^\w{3,30}$/, message: '学号须为3~30位字母/数字/下划线' },
            ]}
          >
            <Input placeholder="学号（同时是登录用户名）" autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="姓名"
            name="realName"
            rules={[
              { required: true, message: '请输入姓名' },
              { max: 50, message: '姓名过长' },
            ]}
          >
            <Input placeholder="真实姓名" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, max: 64, message: '密码长度须为6~64位' },
            ]}
          >
            <Input.Password placeholder="6~64 位" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认密码"
            name="confirm"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) return Promise.resolve();
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password placeholder="再次输入密码" autoComplete="new-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" block>
              注册并登录
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center' }}>
            <Button type="link" style={{ padding: 0 }} onClick={() => navigate('/login')}>
              已有账号？返回登录
            </Button>
          </div>
        </Form>
      </Card>
    </div>
  );
}
