import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type { StudentListItem } from '../../api/studentTypes';
import { ASSIGN_STATUS, fmtTime, statusMeta } from './constants';
import { useMyCourse } from './context';

/** 课程作业列表：GET /api/courses/{courseId}/assignments；未开始作业不可进入题目列表。 */
export default function CourseAssignmentsPage() {
  const { courseId } = useParams();
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const course = useMyCourse(courseId);

  const { data: paged, isLoading, isError, refetch } = useQuery({
    queryKey: ['courseAssignments', courseId, page],
    queryFn: async () => {
      const resp = await client.get(`/courses/${courseId}/assignments`, {
        params: { page, size: 20 },
      });
      return resp.data.data;
    },
  });

  // 课程切换时重置页码，避免小课程出现「空表 + 页码超出」困惑态。
  useEffect(() => {
    setPage(1);
  }, [courseId]);

  const columns: ColumnsType<StudentListItem> = [
    {
      title: '作业',
      dataIndex: 'title',
      render: (title: string, row) =>
        row.status === 'NOT_STARTED' ? (
          <span style={{ color: '#9aa0a6' }}>{title}</span>
        ) : (
          <Button
            type="link"
            style={{ padding: 0, fontWeight: 500 }}
            onClick={() => navigate(`/student/courses/${courseId}/assignments/${row.id}`)}
          >
            {title}
          </Button>
        ),
    },
    { title: '开始时间', dataIndex: 'startAt', width: 150, render: (v: string) => fmtTime(v) },
    { title: '截止时间', dataIndex: 'dueAt', width: 150, render: (v: string) => fmtTime(v) },
    { title: '题目数', dataIndex: 'problemCount', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (s: string) => {
        const m = statusMeta(ASSIGN_STATUS, s);
        return <Tag color={m.color}>{m.label}</Tag>;
      },
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
        <Button type="text" icon={<span>←</span>} onClick={() => navigate('/student/courses')}>
          我的课程
        </Button>
      </div>
      <h2 className="msp-page-title">{course ? `${course.name} · 作业列表` : '课程作业'}</h2>
      <p className="msp-page-sub">作业开始前不可查看题目；已截止作业不可再提交</p>
      {isError && !paged ? (
        <div className="msp-card" style={{ marginTop: 16, textAlign: 'center' }}>
          <Alert type="error" showIcon message="作业列表加载失败，请稍后重试" style={{ marginBottom: 12 }} />
          <Button onClick={() => refetch()}>重试</Button>
        </div>
      ) : (
        <div className="msp-card">
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
      )}
    </div>
  );
}
