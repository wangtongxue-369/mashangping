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

interface AuthContextValue {
  token: string | null;
  user: AuthUser | null;
  login: (req: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

// 注意：本文件按 plan 文件清单命名为 useAuth.ts（非 .tsx），
// 因此 JSX 不可用，用 createElement 渲染 Provider。
export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_STORAGE_KEY));
  const [user, setUser] = useState<AuthUser | null>(() => readStoredUser());

  const login = useCallback(async (req: LoginRequest) => {
    const resp = await client.post<ApiEnvelope<{ token: string; user: AuthUser }>>('/auth/login', req);
    const body = resp.data;
    // 拦截器已保证 code===0 才到达，此处双保险。
    if (body.code !== 0) throw new Error(body.message || '登录失败');
    const { token: t, user: u } = body.data;
    localStorage.setItem(TOKEN_STORAGE_KEY, t);
    localStorage.setItem('msp_user', JSON.stringify(u));
    setToken(t);
    setUser(u);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem('msp_user');
    setToken(null);
    setUser(null);
    window.location.href = '/login';
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ token, user, login, logout }),
    [token, user, login, logout],
  );

  return createElement(AuthContext.Provider, { value }, children);
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