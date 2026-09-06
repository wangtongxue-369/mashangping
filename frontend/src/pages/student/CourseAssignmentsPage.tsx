import { useParams } from 'react-router-dom';

/** 课程作业列表页（骨架：useParams 取 courseId，后续任务填充作业列表与状态徽标）。 */
export default function CourseAssignmentsPage() {
  const { courseId } = useParams();
  return <h2>课程作业（课程 {courseId ?? '未知'}）</h2>;
}
