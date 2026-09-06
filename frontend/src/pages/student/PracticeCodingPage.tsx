import { useQuery } from '@tanstack/react-query';
import { Button, Spin } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import MarkdownView from '../../components/MarkdownView';
import type { PracticeDetail } from '../../api/studentTypes';
import CodingWorkspace from './CodingWorkspace';

/** 公开题阅读 + 练习编码页：MD 题面 + 样例 + CodingWorkspace（练习锚 problemId 提交）。 */
export default function PracticeCodingPage() {
  const { problemId } = useParams();
  const navigate = useNavigate();

  const { data: detail, isLoading } = useQuery<PracticeDetail>({
    queryKey: ['practiceProblemDetail', problemId],
    queryFn: async () => {
      const resp = await client.get(`/practice/problems/${problemId}`);
      return resp.data.data;
    },
    enabled: Boolean(problemId),
  });

  if (isLoading) {
    return <Spin style={{ display: 'block', margin: '48px auto' }} />;
  }

  return (
    <div>
      <Button onClick={() => navigate('/student/practice/problems')}>← 返回题库</Button>
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
            submitTarget={{ type: 'practice', problemId: detail.id }}
            historyAnchor={{ problemId: detail.id }}
          />
        </>
      ) : null}
    </div>
  );
}
