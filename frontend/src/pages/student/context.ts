/**
 * 学生端跨页上下文查询（共享 queryKey，页面间复用缓存）：
 * - useMyCourse：课程名（GET /courses/my 内查）
 * - useAssignmentHeader：作业上下文头（GET /api/assignments/{id} 学生分支，状态/时间窗/课程名）
 */
import { useQuery } from '@tanstack/react-query';
import { client } from '../../api/client';
import type { MyCourseView, StudentAssignmentHeader } from '../../api/studentTypes';

/** 我的课程缓存 key（MyCoursesPage 与课程作业列表页共用）。 */
export const MY_COURSES_KEY = ['myCourses'];

/** 作业上下文头缓存 key。 */
export const ASSIGN_HEADER_KEY = 'assignmentHeader';

export function useMyCourse(courseId?: string): MyCourseView | undefined {
  const { data } = useQuery<MyCourseView[]>({
    queryKey: MY_COURSES_KEY,
    queryFn: async () => {
      const resp = await client.get('/courses/my');
      return resp.data.data;
    },
  });
  // 防御性校验：仅当后端返回数组时才查名（数据异常按无课程名处理，不阻塞列表渲染）。
  const list = Array.isArray(data) ? data : [];
  return list.find((c) => c.courseId === Number(courseId));
}

export function useAssignmentHeader(courseId?: string, assignmentId?: string) {
  return useQuery<StudentAssignmentHeader>({
    queryKey: [ASSIGN_HEADER_KEY, courseId, assignmentId],
    queryFn: async () => {
      const resp = await client.get(`/assignments/${assignmentId}`);
      return resp.data.data;
    },
    enabled: Boolean(assignmentId),
  });
}
