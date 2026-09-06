import { useParams } from 'react-router-dom';

/** 练习题阅读 + 编码页（骨架：useParams 取 problemId，后续任务复用 CodingWorkspace 提交）。 */
export default function PracticeCodingPage() {
  const { problemId } = useParams();
  return <h2>练习编码页（题目 {problemId ?? '未知'}）</h2>;
}
