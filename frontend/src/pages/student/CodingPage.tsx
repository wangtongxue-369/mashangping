import { useParams } from 'react-router-dom';

/** 作业题目阅读 + 编码页（骨架：useParams 取 courseId/assignmentId/problemId，后续任务填充 CodingWorkspace）。 */
export default function CodingPage() {
  const { courseId, assignmentId, problemId } = useParams();
  return (
    <h2>
      编码页（课程 {courseId ?? '未知'} / 作业 {assignmentId ?? '未知'} / 题目{' '}
      {problemId ?? '未知'}）
    </h2>
  );
}
