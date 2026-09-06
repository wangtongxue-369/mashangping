import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { AssignmentStatus, StudentListItem } from '../../api/studentTypes';

/** 状态徽标：作业实时状态 → 展示文案与 Tag 颜色。 */
const STATUS_MAP: Record<AssignmentStatus, { label: string; color: string }> = {
  NOT_STARTED: { label: '未开始', color: 'default' },
  IN_PROGRESS: { label: '进行中', color: 'blue' },
  LATE_WINDOW: { label: '宽限中', color: 'orange' },
  CLOSED: { label: '已截止', color: 'default' },
};

function formatTime(iso: string): string {
  if (!iso) return '-';
  return iso.replace('T', ' ').slice(0, 16);
}

/** 课程作业列表：GET /api/courses/{courseId}/assignments；未开始作业不可进入题目列表。 */
export default function CourseAssignmentsPage() {
  const { courseId } = useParams();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);

  const { data: paged, isLoading } = useQuery({
    queryKey: ['courseAssignments', courseId, page],
    queryFn: async () => {
      const resp = await client.get(`/courses/${courseId}/assignments`, {
        params: { page, size: 20 },
      });
      return resp.data.data;
    },
  });

  const columns: ColumnsType<StudentListItem> = [
    {
      title: '作业',
      dataIndex: 'title',
      render: (title: string, row) =>
        row.status === 'NOT_STARTED' ? (
          <span style={{ color: '#999' }}>{title}</span>
        ) : (
          <Button
            type="link"
            onClick={() => navigate(`/student/courses/${courseId}/assignments/${row.id}`)}
          >
            {title}
          </Button>
        ),
    },
    {
      title: '开始时间',
      dataIndex: 'startAt',
      render: (v: string) => formatTime(v),
    },
    {
      title: '截止时间',
      dataIndex: 'dueAt',
      render: (v: string) => formatTime(v),
    },
    { title: '题目数', dataIndex: 'problemCount', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (s: AssignmentStatus) => {
        const m = STATUS_MAP[s] ?? { label: String(s), color: 'default' };
        return <Tag color={m.color}>{m.label}</Tag>;
      },
    },
  ];

  return (
    <div>
      <Button onClick={() => navigate('/student/courses')}>← 返回我的课程</Button>
      <h2>课程作业</h2>
      <Table<StudentListItem>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={paged?.records ?? []}
        pagination={{
          current: page,
          pageSize: 20,
          total: paged?.total ?? 0,
          onChange: setPage,
          showSizeChanger: false,
        }}
      />
    </div>
  );
}