import axios from 'axios';

const TOKEN_KEY = 'msp_token';

/** JWT 存取键，供 useAuth 与拦截器共享。 */
export const TOKEN_STORAGE_KEY = TOKEN_KEY;

export const client = axios.create({ baseURL: '/api' });

/** 从拦截器/axios 错误中取业务提示文案：业务错误 reject 出的是 {code,message} body，其余兜底。 */
export function extractApiMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'message' in err) {
    const m = (err as { message?: unknown }).message;
    if (typeof m === 'string' && m) return m;
  }
  return fallback;
}

client.interceptors.request.use((cfg) => {
  const t = localStorage.getItem(TOKEN_KEY);
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});

client.interceptors.response.use(
  (resp) => {
    const body = resp.data;
    // 业务错误：HTTP200 + code≠0 —— 交调用方按 message 提示，不静默吞。
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(body);
    }
    return resp;
  },
  (err) => {
    const status = err.response?.status;
    // 401：清 token 并（非 /login 时）重定向，避免登录页上的 401 引起整页跳转环。
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    } else if (status !== undefined && status >= 500) {
      // 5xx：转成业务风格错误，让调用方呈现友好提示，而非 axios 的“Request failed”。
      return Promise.reject({ code: status, message: '服务暂不可用，请稍后重试', data: null });
    }
    return Promise.reject(err);
  },
);