/**
 * 教师端跨页上下文查询（共享 queryKey，页面间复用缓存）：
 * - useTeacherCourse：课程详情 GET /api/courses/{courseId}（教师属主）
 * - useTeacherAssignment：作业详情 GET /api/assignments/{id}（已含 courseId/courseName/title）
 */
import { useQuery } from '@tanstack/react-query';
import { client } from '../../api/client';
import type { AssignmentTeacherDetail, CourseView } from '../../api/types';

export function useTeacherCourse(courseId?: string): CourseView | undefined {
  const { data } = useQuery<CourseView>({
    queryKey: ['teacherCourse', courseId],
    queryFn: async () => {
      const resp = await client.get(`/courses/${courseId}`);
      return resp.data.data;
    },
    enabled: Boolean(courseId),
  });
  return data;
}

export function useTeacherAssignment(assignmentId?: string): AssignmentTeacherDetail | undefined {
  const { data } = useQuery<AssignmentTeacherDetail>({
    queryKey: ['assignment', assignmentId],
    queryFn: async () => {
      const resp = await client.get(`/assignments/${assignmentId}`);
      return resp.data.data;
    },
    enabled: Boolean(assignmentId),
  });
  return data;
}
