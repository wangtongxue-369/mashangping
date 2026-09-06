import { Breadcrumb } from 'antd';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/**
 * 教师端统一三层面包屑：全部课程 / 课程工作台 / 当前页。
 * 层级由传入的 courseName / assignmentTitle 决定（页面各自从详情/课程查询取名字）。
 * 用法示例（作业详情页）：
 *   <CourseBreadcrumb
 *     courseName={detail?.courseName}
 *     courseId={detail?.courseId}
 *     assignmentTitle={detail?.title}
 *     assignmentId={detail?.id}
 *     current="题目管理"
 *   />
 */
export default function CourseBreadcrumb({
  courseId,
  courseName,
  assignmentId,
  assignmentTitle,
  current,
}: {
  courseId?: number;
  courseName?: string;
  assignmentId?: number;
  assignmentTitle?: string;
  current: string;
}) {
  const items: { title: ReactNode }[] = [{ title: <Link to="/teacher/courses">全部课程</Link> }];
  if (courseId) {
    items.push({
      title: courseName ? (
        <Link to={`/teacher/courses/${courseId}`}>{courseName} · 工作台</Link>
      ) : (
        <Link to={`/teacher/courses/${courseId}`}>课程工作台</Link>
      ),
    });
  }
  if (assignmentId) {
    items.push({
      title: assignmentTitle ? (
        <Link to={`/teacher/assignments/${assignmentId}`}>{assignmentTitle}</Link>
      ) : (
        <Link to={`/teacher/assignments/${assignmentId}`}>作业详情</Link>
      ),
    });
  }
  items.push({ title: current });
  return <Breadcrumb items={items} style={{ marginBottom: 12 }} />;
}
