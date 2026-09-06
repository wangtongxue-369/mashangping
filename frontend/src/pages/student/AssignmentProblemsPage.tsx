import { useQuery } from '@tanstack/react-query';
import { Button, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { StudentProblemItem } from '../../api/studentTypes';
import { LANGUAGE_LABEL } from './constants';

/** 作业题目列表：GET /api/courses/{courseId}/assignments/{assignmentId}/problems，点击进入编码页。 */
export default function AssignmentProblemsPage() {
  const { courseId, assignmentId } = useParams();
  const navigate = useNavigate();

  const { data: list, isLoading } = useQuery<StudentProblemItem[]>({
    queryKey: ['assignmentProblems', courseId, assignmentId],
    queryFn: async () => {
      const resp = await client.get(`/courses/${courseId}/assignments/${assignmentId}/problems`);
      return resp.data.data;
    },
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

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
        <Button type="text" icon={<span>←</span>} onClick={() => navigate(`/student/courses/${courseId}/assignments`)}>
          作业列表
        </Button>
      </div>
      <h2 className="msp-page-title">作业题目</h2>
      <p className="msp-page-sub">点击题目进入作答：阅读题面并在右侧编码提交</p>
      <div className="msp-card">
        <Table<StudentProblemItem>
          rowKey="problemId"
          loading={isLoading}
          columns={columns}
          dataSource={list ?? []}
          pagination={false}
        />
      </div>
    </div>
  );
}
