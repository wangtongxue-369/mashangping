import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

/** 角色守卫可选的角色取值：STUDENT 学生端 / TEACHER 教师端。 */
export type GuardRole = 'STUDENT' | 'TEACHER';

/**
 * 路由守卫：无 token 一律重定向 /login。
 * 传入可选 role 时，若当前用户角色不符则跳转到对应角色的首页
 * （STUDENT → /student/courses，其余 → /teacher/courses）。
 * 不传 role 保持仅 token 校验（兼容教师端与既有用法）。
 */
export default function ProtectedRoute({ children, role }: { children: ReactNode; role?: GuardRole }) {
  const { token, user } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  if (role && user?.role !== role) {
    // 跳转到当前用户自己的首页，避免跳回仍由本守卫拦截的路径造成循环。
    return <Navigate to={user?.role === 'STUDENT' ? '/student/courses' : '/teacher/courses'} replace />;
  }
  return <>{children}</>;
}
