import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { client } from '../../api/client';
import type { PracticeSummary } from '../../api/studentTypes';
import { LANGUAGE_LABEL } from './constants';

/** 公开题库浏览：GET /api/practice/problems 分页列表，点击进入练习编码页。 */
export default function PracticePage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);

  const { data: paged, isLoading } = useQuery({
    queryKey: ['practiceProblems', page],
    queryFn: async () => {
      const resp = await client.get('/practice/problems', { params: { page, size: 20 } });
      return resp.data.data;
    },
  });

  const columns: ColumnsType<PracticeSummary> = [
    {
      title: '题号',
      width: 70,
      render: (_, __, index) => (
        <span style={{ color: '#9aa0a6' }}>{(page - 1) * 20 + index + 1}</span>
      ),
    },
    {
      title: '题目',
      dataIndex: 'title',
      render: (title: string, row) => (
        <Button type="link" style={{ padding: 0, fontWeight: 500 }} onClick={() => navigate(`/student/practice/problems/${row.id}`)}>
          {title}
        </Button>
      ),
    },
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
      <h2 className="msp-page-title">自由练习</h2>
      <p className="msp-page-sub">平台公开题库，随时练习、实时判题</p>
      <div className="msp-card">
        <Table<PracticeSummary>
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
    </div>
  );
}
