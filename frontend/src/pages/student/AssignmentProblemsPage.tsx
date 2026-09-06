import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { StudentProblemItem } from '../../api/studentTypes';
import { ASSIGN_STATUS, LANGUAGE_LABEL, fmtDateTime, statusMeta } from './constants';
import { useAssignmentHeader } from './context';
import DeadlineCountdown from './DeadlineCountdown';

/** 作业题目列表：GET /api/courses/{courseId}/assignments/{assignmentId}/problems，点击进入编码页。 */
export default function AssignmentProblemsPage() {
  const { courseId, assignmentId } = useParams();
  const navigate = useNavigate();

  const { data: header } = useAssignmentHeader(courseId, assignmentId);
  const status = header?.status;

  const { data: list, isLoading, isError, refetch } = useQuery<StudentProblemItem[]>({
    queryKey: ['assignmentProblems', courseId, assignmentId],
    queryFn: async () => {
      const resp = await client.get(`/courses/${courseId}/assignments/${assignmentId}/problems`);
      return resp.data.data;
    },
    enabled: Boolean(courseId && assignmentId),
  });

  const columns: ColumnsType<StudentProblemItem> = [
    {
      title: '题号',
      width: 70,
      render: (_, __, index) => <span style={{ color: '#9aa0a6' }}>{index + 1}</span>,
    },
    {
      title: '题目',
      dataIndex: 'title',
      render: (title: string, row) => (
        <Button
          type="link"
          style={{ padding: 0, fontWeight: 500 }}
          onClick={() =>
            navigate(`/student/courses/${courseId}/assignments/${assignmentId}/problems/${row.problemId}`)
          }
        >
          {title}
        </Button>
      ),
    },
    { title: '分值', dataIndex: 'score', width: 90 },
    {
      title: '语言',
      dataIndex: 'languages',
      render: (langs: string[]) =>
        (langs ?? []).map((l) => (
          <Tag key={l} style={{ marginInlineEnd: 4 }}>
            {LANGUAGE_LABEL[l] ?? l}
          </Tag>
        )),
    },
    { title: '时限', dataIndex: 'timeLimitMs', width: 90, render: (v: number) => `${v}ms` },
    { title: '内存', dataIndex: 'memoryLimitMb', width: 90, render: (v: number) => `${v}MB` },
  ];

  const lateEndIso = header?.dueAt;

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
        <Button type="text" icon={<span>←</span>} onClick={() => navigate(`/student/courses/${courseId}/assignments`)}>
          作业列表
        </Button>
      </div>
      <h2 className="msp-page-title">{header ? `${header.courseName} · ${header.title}` : '作业题目'}</h2>
      <p className="msp-page-sub">
        {header ? (
          <Space size={10} wrap>
            {status ? <Tag color={statusMeta(ASSIGN_STATUS, status).color}>{statusMeta(ASSIGN_STATUS, status).label}</Tag> : null}
            <span>共 {header.problemCount} 题</span>
            <span>截止 {fmtDateTime(header.dueAt)}</span>
            {lateEndIso && (status === 'IN_PROGRESS' || status === 'LATE_WINDOW') ? (
              <DeadlineCountdown
                targetIso={lateEndIso}
                lateDays={status === 'LATE_WINDOW' ? (header?.lateDays ?? 0) : 0}
                label={status === 'LATE_WINDOW' ? '最终截止' : '截止'}
              />
            ) : null}
          </Space>
        ) : (
          '点击题目进入作答：阅读题面并在右侧编码提交'
        )}
      </p>
      {status === 'CLOSED' ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="作业已截止，仍可查看题目与历史提交，但不可再提交代码。"
        />
      ) : null}
      {status === 'LATE_WINDOW' ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message="已过截止时间，仍在宽限期内：仍可提交，但本次提交将标记为「迟交」。"
        />
      ) : null}
      {isError && !list ? (
        <div className="msp-card" style={{ marginTop: 16, textAlign: 'center' }}>
          <p style={{ color: '#6b7280' }}>题目列表加载失败，请稍后重试。</p>
          <Button onClick={() => refetch()}>重试</Button>
        </div>
      ) : (
        <div className="msp-card">
          <Table<StudentProblemItem>
            rowKey="problemId"
            loading={isLoading}
            columns={columns}
            dataSource={list ?? []}
            pagination={false}
          />
        </div>
      )}
    </div>
  );
}
