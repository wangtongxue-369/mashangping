import { useQuery } from '@tanstack/react-query';
import { Button, Spin } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { PracticeDetail } from '../../api/studentTypes';
import CodingWorkspace from './CodingWorkspace';

/** 公开题阅读 + 练习编码页：取题详情后交给双栏 CodingWorkspace（练习锚 problemId 提交）。 */
export default function PracticeCodingPage() {
  const { problemId } = useParams();
  const navigate = useNavigate();

  const { data: detail, isLoading, isError } = useQuery<PracticeDetail>({
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
  if (isError || !detail) {
    return (
      <div className="msp-card" style={{ marginTop: 16, textAlign: 'center' }}>
        <p style={{ color: '#6b7280' }}>题目加载失败，请稍后重试。</p>
        <Button onClick={() => navigate('/student/practice/problems')}>返回题库</Button>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
        <Button type="text" icon={<span>←</span>} onClick={() => navigate('/student/practice/problems')}>
          返回题库
        </Button>
        <span style={{ color: '#9aa0a6', fontSize: 13 }}>自由练习</span>
      </div>
      <CodingWorkspace
        title={detail.title}
        description={detail.description}
        samples={detail.samples}
        languages={detail.languages}
        timeLimitMs={detail.timeLimitMs}
        memoryLimitMb={detail.memoryLimitMb}
        submitTarget={{ type: 'practice', problemId: detail.id }}
        historyAnchor={{ problemId: detail.id }}
        typeTag="自由练习"
      />
    </div>
  );
}
