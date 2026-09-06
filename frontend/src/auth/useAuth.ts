import { createContext, createElement, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { client, TOKEN_STORAGE_KEY } from '../api/client';

export interface AuthUser {
  id: number;
  username: string;
  realName: string;
  studentNo: string;
  role: string;
}

/** 后端统一信封 ApiResponse<T>：{ code, message, data }。 */
export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface LoginRequest {
  username: string;
  password: string;
}

/** 学生自助注册入参（对齐后端 RegisterRequest）。 */
export interface RegisterRequest {
  email: string;
  code: string;
  studentNo: string;
  realName: string;
  password: string;
}

interface AuthContextValue {
  token: string | null;
  user: AuthUser | null;
  /** 登录成功后返回用户快照（调用方按 role 分流）。 */
  login: (req: LoginRequest) => Promise<AuthUser>;
  /** 注册即登录：后端注册成功直接签发会话，落库后返回用户快照。 */
  register: (req: RegisterRequest) => Promise<AuthUser>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

// 注意：本文件按 plan 文件清单命名为 useAuth.ts（非 .tsx），
// 因此 JSX 不可用，用 createElement 渲染 Provider。
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_STORAGE_KEY));
  const [user, setUser] = useState<AuthUser | null>(() => readStoredUser());

  const login = useCallback(async (req: LoginRequest): Promise<AuthUser> => {
    const resp = await client.post<ApiEnvelope<{ token: string; user: AuthUser }>>('/auth/login', req);
    const body = resp.data;
    // 拦截器已保证 code===0 才到达，此处双保险。
    if (body.code !== 0) throw new Error(body.message || '登录失败');
    applySession(body.data.token, body.data.user);
    setToken(body.data.token);
    setUser(body.data.user);
    return body.data.user;
  }, []);

  const register = useCallback(async (req: RegisterRequest): Promise<AuthUser> => {
    const resp = await client.post<ApiEnvelope<{ token: string; user: AuthUser }>>('/auth/register', req);
    const body = resp.data;
    if (body.code !== 0) throw new Error(body.message || '注册失败');
    applySession(body.data.token, body.data.user);
    setToken(body.data.token);
    setUser(body.data.user);
    return body.data.user;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem('msp_user');
    setToken(null);
    setUser(null);
    // 已在 /login（如 ADMIN 提示后原地登出）不重复整页跳转，避免无意义刷新。
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, login, register, logout }),
    [token, user, login, register, logout],
  );

  return createElement(AuthContext.Provider, { value }, children);
}

/** 落库会话：token + 用户快照（登录/注册共用，双键与拦截器一致）。 */
function applySession(token: string, user: AuthUser): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
  localStorage.setItem('msp_user', JSON.stringify(user));
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 必须在 <AuthProvider> 内使用');
  return ctx;
}

function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem('msp_user');
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}