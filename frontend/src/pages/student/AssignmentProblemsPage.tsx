import { useParams } from 'react-router-dom';

/** 作业题目列表页（骨架：useParams 取 courseId/assignmentId，后续任务填充题目列表）。 */
export default function AssignmentProblemsPage() {
  const { courseId, assignmentId } = useParams();
  return (
    <h2>
      作业题目列表（课程 {courseId ?? '未知'} / 作业 {assignmentId ?? '未知'}）
    </h2>
  );
}
