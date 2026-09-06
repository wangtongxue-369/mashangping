import { useQuery } from '@tanstack/react-query';
import { Button, Space, Spin, Tag } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { StudentProblemDetail } from '../../api/studentTypes';
import CodingWorkspace from './CodingWorkspace';
import { ASSIGN_STATUS, statusMeta } from './constants';
import { useAssignmentHeader } from './context';

/** 作业题目阅读 + 编码页：取题详情 + 作业上下文头后交给双栏 CodingWorkspace（作业锚提交，状态贯穿作答）。 */
export default function CodingPage() {
  const { courseId, assignmentId, problemId } = useParams();
  const navigate = useNavigate();

  const { data: header, isError: headerError } = useAssignmentHeader(courseId, assignmentId);
  const status = header?.status;

  const { data: detail, isLoading, isError } = useQuery<StudentProblemDetail>({
    queryKey: ['assignmentProblemDetail', courseId, assignmentId, problemId],
    queryFn: async () => {
      const resp = await client.get(`/courses/${courseId}/assignments/${assignmentId}/problems/${problemId}`);
      return resp.data.data;
    },
    enabled: Boolean(courseId && assignmentId && problemId),
  });

  const loading = isLoading || (!detail && !isError);
  if (loading) {
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
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
        <Button
          type="text"
          icon={<span>←</span>}
          onClick={() => navigate(`/student/courses/${courseId}/assignments/${assignmentId}`)}
        >
          题目列表
        </Button>
      </div>
      <div style={{ marginBottom: 12 }}>
        <h2 className="msp-page-title" style={{ marginBottom: 2 }}>
          {header ? `${header.title} · ${detail.title}` : detail.title}
        </h2>
        <Space size={10} wrap>
          {header?.courseName ? <span style={{ color: '#6b7280', fontSize: 13 }}>{header.courseName}</span> : null}
          {header ? (
            <Tag color={statusMeta(ASSIGN_STATUS, header.status).color}>{statusMeta(ASSIGN_STATUS, header.status).label}</Tag>
          ) : null}
          {status === 'LATE_WINDOW' ? <Tag color="orange">宽限中：提交将标记为迟交</Tag> : null}
          {status === 'CLOSED' ? <Tag color="default">已截止：仅可查看，不可提交</Tag> : null}
        </Space>
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
        assignmentMeta={
          header && !headerError
            ? {
                status: header.status,
                dueAt: header.dueAt,
                lateDays: header.lateDays,
                fullScore: detail.score,
              }
            : undefined
        }
      />
    </div>
  );
}
