import axios from 'axios';

const TOKEN_KEY = 'msp_token';

/** JWT 存取键，供 useAuth 与拦截器共享。 */
export const TOKEN_STORAGE_KEY = TOKEN_KEY;

export const client = axios.create({ baseURL: '/api' });

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
    if (err.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      window.location.href = '/login';
    }
    return Promise.reject(err);
  },
);