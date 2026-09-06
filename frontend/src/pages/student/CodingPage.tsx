import { useQuery } from '@tanstack/react-query';
import { Button, Spin } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import MarkdownView from '../../components/MarkdownView';
import type { StudentProblemDetail } from '../../api/studentTypes';
import CodingWorkspace from './CodingWorkspace';

/** 作业题目阅读 + 编码页：题面(MD) + 样例点示例输入输出 + CodingWorkspace（作业锚提交）。 */
export default function CodingPage() {
  const { courseId, assignmentId, problemId } = useParams();
  const navigate = useNavigate();

  const { data: detail, isLoading } = useQuery<StudentProblemDetail>({
    queryKey: ['assignmentProblemDetail', courseId, assignmentId, problemId],
    queryFn: async () => {
      const resp = await client.get(`/courses/${courseId}/assignments/${assignmentId}/problems/${problemId}`);
      return resp.data.data;
    },
    enabled: Boolean(courseId && assignmentId && problemId),
  });

  if (isLoading) {
    return <Spin style={{ display: 'block', margin: '48px auto' }} />;
  }

  return (
    <div>
      <Button onClick={() => navigate(`/student/courses/${courseId}/assignments/${assignmentId}`)}>
        ← 返回题目列表
      </Button>
      {detail ? (
        <>
          <h2>{detail.title}</h2>
          <div style={{ marginBottom: 8, color: '#666' }}>
            限时 {detail.timeLimitMs}ms / 内存 {detail.memoryLimitMb}MB
          </div>
          <MarkdownView source={detail.description} />
          <h3 style={{ marginTop: 16 }}>样例</h3>
          {detail.samples.length === 0 ? (
            <div>本题无样例。</div>
          ) : (
            detail.samples.map((s, i) => (
              <div key={i} style={{ marginBottom: 12 }}>
                <b>样例 {i + 1}</b>
                <div>输入</div>
                <pre style={{ background: '#f5f5f5', padding: 8 }}>{s.input}</pre>
                <div>输出</div>
                <pre style={{ background: '#f5f5f5', padding: 8 }}>{s.output}</pre>
              </div>
            ))
          )}
          <hr style={{ margin: '20px 0', border: 'none', borderTop: '1px solid #eee' }} />
          <CodingWorkspace
            languages={detail.languages}
            submitTarget={{ type: 'assignment', assignmentProblemId: detail.assignmentProblemId }}
            historyAnchor={{ assignmentProblemId: detail.assignmentProblemId }}
          />
        </>
      ) : null}
    </div>
  );
}
