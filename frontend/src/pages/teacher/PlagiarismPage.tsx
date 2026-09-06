import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, InputNumber, Progress, Space, Spin, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate, useParams } from 'react-router-dom';
import { client, extractApiMessage } from '../../api/client';
import type { PlagiarismCompare, PlagiarismPair, PlagiarismReport } from '../../api/plagiarismTypes';

/** 行级启发式高亮：某行「规范化后」出现在对方源码即视为雷同片段。 */
export function markSharedLines(codeA: string, codeB: string): { aShared: Set<number>; bShared: Set<number> } {
  const norm = (line: string) => line.trim().replace(/\s+/g, ' ').toLowerCase();
  const aLines = codeA.split('\n').map(norm);
  const bLines = codeB.split('\n').map(norm);
  const bKeys = new Set(bLines.filter((l) => l.length > 2));
  const aKeys = new Set(aLines.filter((l) => l.length > 2));
  const aShared = new Set<number>();
  const bShared = new Set<number>();
  aLines.forEach((l, i) => {
    if (l.length > 2 && bKeys.has(l)) aShared.add(i);
  });
  bLines.forEach((l, i) => {
    if (l.length > 2 && aKeys.has(l)) bShared.add(i);
  });
  return { aShared, bShared };
}

/** 学生名 + 学号 + 语言/得分摘要。 */
function stuLabel(info: PlagiarismCompare['a']) {
  return `${info.realName}（${info.studentNo}）·${info.language}${info.score != null ? ` ${info.score}分` : ''}${info.ac ? '·AC' : ''}`;
}

function codePane(code: string, shared: Set<number>, title: string) {
  return (
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontWeight: 600, marginBottom: 8 }}>{title}</div>
      <pre
        style={{
          margin: 0,
          background: '#f6f8fa',
          border: '1px solid #eceef1',
          borderRadius: 8,
          padding: 12,
          fontSize: 13,
          lineHeight: 1.6,
          maxHeight: '62vh',
          overflow: 'auto',
        }}
      >
        {code.split('\n').map((line, i) => (
          <div
            key={i}
            style={
              shared.has(i)
                ? { background: '#ffe3e3', color: '#c00', borderRadius: 3, padding: '0 2px' }
                : undefined
            }
          >
            <span style={{ color: '#b0b8c4', userSelect: 'none', marginRight: 8 }}>{i + 1}</span>
            {line || ' '}
          </div>
        ))}
      </pre>
    </div>
  );
}

/** 教师端查重报告页：该作业该题同班两两相似度 + 左右代码对比高亮。 */
export default function PlagiarismPage() {
  const { assignmentId, problemId } = useParams<{ assignmentId: string; problemId: string }>();
  const navigate = useNavigate();
  const [high, setHigh] = useState(0.8);
  const [compare, setCompare] = useState<PlagiarismPair | null>(null);

  const reportQuery = useQuery<PlagiarismReport>({
    queryKey: ['plagiarism', assignmentId, problemId, high],
    queryFn: () =>
      client
        .get<{ data: PlagiarismReport }>(`/assignments/${assignmentId}/plagiarism/problems/${problemId}`, {
          params: { includeMin: 0.5, high },
        })
        .then((r) => r.data.data),
    enabled: Boolean(assignmentId && problemId),
  });

  const compareQuery = useQuery<PlagiarismCompare>({
    queryKey: ['plagiarismCompare', compare?.a.submissionId, compare?.b.submissionId],
    queryFn: () =>
      client
        .get<{ data: PlagiarismCompare }>(
          `/assignments/${assignmentId}/plagiarism/problems/${problemId}/compare`,
          { params: { submissionA: compare?.a.submissionId, submissionB: compare?.b.submissionId } },
        )
        .then((r) => r.data.data),
    enabled: Boolean(compare && assignmentId && problemId),
  });

  const report = reportQuery.data;

  const columns: ColumnsType<PlagiarismPair> = [
    {
      title: '学生 A',
      dataIndex: 'a',
      render: (a: PlagiarismPair['a']) => (
        <span>
          {a.realName}
          <span style={{ color: '#9aa0a6', marginLeft: 6 }}>{a.studentNo}</span>
        </span>
      ),
    },
    {
      title: '学生 B',
      dataIndex: 'b',
      render: (b: PlagiarismPair['b']) => (
        <span>
          {b.realName}
          <span style={{ color: '#9aa0a6', marginLeft: 6 }}>{b.studentNo}</span>
        </span>
      ),
    },
    {
      title: '相似度',
      dataIndex: 'similarity',
      width: 240,
      render: (v: number) => (
        <Space>
          <Progress
            percent={Math.round(v * 100)}
            size="small"
            style={{ width: 130 }}
            strokeColor={v >= report?.highThreshold! ? '#ff4d4f' : '#fa8c16'}
          />
          <b>{(v * 100).toFixed(1)}%</b>
        </Space>
      ),
    },
    {
      title: '等级',
      dataIndex: 'flag',
      width: 90,
      render: (f: string) => (f === 'HIGH' ? <Tag color="red">高疑似</Tag> : <Tag color="orange">关注</Tag>),
    },
  ];

  const share = useMemo(
    () =>
      compareQuery.data ? markSharedLines(compareQuery.data.codeA, compareQuery.data.codeB) : null,
    [compareQuery.data],
  );

  return (
    <div>
      <Button onClick={() => navigate(`/teacher/assignments/${assignmentId}`)}>← 返回作业</Button>
      <h2 className="msp-page-title">代码查重</h2>
      <p className="msp-page-sub">
        {report ? `${report.problemTitle} · 参与学生 ${report.participants} 人` : '加载中…'}
      </p>
      <Space style={{ marginBottom: 12 }}>
        <span>高疑似阈值：</span>
        <InputNumber
          min={0}
          max={1}
          step={0.05}
          value={high}
          onChange={(v) => setHigh(v ?? 0.8)}
          style={{ width: 110 }}
        />
        <Button type="primary" onClick={() => reportQuery.refetch()} loading={reportQuery.isFetching}>
          重新查重
        </Button>
      </Space>
      {reportQuery.isError ? (
        <div style={{ color: '#c00', margin: 12 }}>{extractApiMessage(reportQuery.error, '报告加载失败')}</div>
      ) : null}
      <Table<PlagiarismPair>
        rowKey={(r) => `${r.a.submissionId}-${r.b.submissionId}`}
        loading={reportQuery.isLoading}
        columns={columns}
        dataSource={report?.items ?? []}
        pagination={false}
        onRow={(row) => ({ onClick: () => setCompare(row), style: { cursor: 'pointer' } })}
        locale={{ emptyText: '未发现相似度 ≥ 50% 的代码对；可降低阈值后重新查重。' }}
      />
      <Drawer
        title={
          compare && compareQuery.data
            ? `对比：${stuLabel(compareQuery.data.a)}  ↔  ${stuLabel(compareQuery.data.b)}`
            : '代码对比'
        }
        width="min(92vw, 1180px)"
        open={compare != null}
        onClose={() => setCompare(null)}
      >
        {compareQuery.isLoading || !share || !compareQuery.data ? (
          <Spin />
        ) : (
          <div style={{ display: 'flex', gap: 16 }}>
            {codePane(compareQuery.data.codeA, share.aShared, '学生 A 代码（最高分提交）')}
            {codePane(compareQuery.data.codeB, share.bShared, '学生 B 代码（最高分提交）')}
          </div>
        )}
      </Drawer>
    </div>
  );
}
