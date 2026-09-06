/**
 * 学生自助注册 API（对齐后端 RegisterController）：
 * - sendRegisterCode: POST /api/auth/register/code —— 签发邮箱验证码（60s 频率/日限由后端兜底）
 * - 实际注册走 useAuth.register（成功即登录落会话），此处只保留发码。
 */
import { client } from './client';

export async function sendRegisterCode(email: string): Promise<void> {
  await client.post('/auth/register/code', { email });
}
