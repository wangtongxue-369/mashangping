import { useQuery } from '@tanstack/react-query';
import { Button, Spin } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { StudentProblemDetail } from '../../api/studentTypes';
import CodingWorkspace from './CodingWorkspace';

/** 作业题目阅读 + 编码页：取题详情后交给双栏 CodingWorkspace（作业锚提交）。 */
export default function CodingPage() {
  const { courseId, assignmentId, problemId } = useParams();
  const navigate = useNavigate();

  const { data: detail, isLoading, isError } = useQuery<StudentProblemDetail>({
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
  if (isError || !detail) {
    return (
      <div className="msp-card" style={{ marginTop: 16, textAlign: 'center' }}>
        <p style={{ color: '#6b7280' }}>题目加载失败或不可见（作业可能尚未开始）。</p>
        <Button onClick={() => navigate(`/student/courses/${courseId}/assignments/${assignmentId}`)}>
          返回题目列表
        </Button>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
        <Button
          type="text"
          icon={<span>←</span>}
          onClick={() => navigate(`/student/courses/${courseId}/assignments/${assignmentId}`)}
        >
          题目列表
        </Button>
        <span style={{ color: '#9aa0a6', fontSize: 13 }}>
          {courseId && assignmentId ? `课程 ${courseId} / 作业 ${assignmentId}` : ''}
        </span>
      </div>
      <CodingWorkspace
        title={detail.title}
        description={detail.description}
        samples={detail.samples}
        languages={detail.languages}
        timeLimitMs={detail.timeLimitMs}
        memoryLimitMb={detail.memoryLimitMb}
        submitTarget={{ type: 'assignment', assignmentProblemId: detail.assignmentProblemId }}
        historyAnchor={{ assignmentProblemId: detail.assignmentProblemId }}
        typeTag="作业题"
      />
    </div>
  );
}
