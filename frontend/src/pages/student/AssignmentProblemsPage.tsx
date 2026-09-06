import { useQuery } from '@tanstack/react-query';
import { Button, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { StudentProblemItem } from '../../api/studentTypes';

/** 语言键 → 展示名（对齐 CodingWorkspace 的 LANGUAGE_META）。 */
const LANG_LABEL: Record<string, string> = {
  C: 'C',
  CPP: 'C++',
  JAVA: 'Java',
  PYTHON: 'Python',
};

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
      title: '题目',
      dataIndex: 'title',
      render: (title: string, row) => (
        <Button
          type="link"
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
            {LANG_LABEL[l] ?? l}
          </Tag>
        )),
    },
    { title: '时限', dataIndex: 'timeLimitMs', width: 90, render: (v: number) => `${v}ms` },
    { title: '内存', dataIndex: 'memoryLimitMb', width: 90, render: (v: number) => `${v}MB` },
  ];

  return (
    <div>
      <Button onClick={() => navigate(`/student/courses/${courseId}/assignments`)}>← 返回作业列表</Button>
      <h2>作业题目</h2>
      <Table<StudentProblemItem>
        rowKey="problemId"
        loading={isLoading}
        columns={columns}
        dataSource={list ?? []}
        pagination={false}
      />
    </div>
  );
}
