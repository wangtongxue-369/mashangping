import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Drawer,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import Editor from '@monaco-editor/react';
import { client, extractApiMessage } from '../../api/client';
import type { ApiResponse, Page } from '../../api/types';
import type {
  GradebookView,
  MaskedPoint,
  SamplePoint,
  TeacherSubmissionDetail,
  TeacherSubmissionRow,
} from '../../api/gradebookTypes';
import { SUBMIT_STATUS_META as STATUS_META } from './constants';

function StatusTag({ status }: { status: string }) {
  const meta = STATUS_META[status];
  return (
    <Tag color={meta?.color}>{meta?.label ?? status}</Tag>
  );
}

/** 后端语言键（JudgeLanguage 枚举）→ Monaco 语言 id。 */
const MONACO_LANG: Record<string, string> = {
  C: 'c',
  CPP: 'cpp',
  JAVA: 'java',
  PYTHON: 'python',
};

/** 提交历史页：题目/学生筛选 + 分页列表 + 详情抽屉（Monaco 只读代码 + 逐点判题明细）。 */
export default function SubmissionsPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>();
  const { message } = AntApp.useApp();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [apId, setApId] = useState<number | undefined>(undefined);
  const [studentId, setStudentId] = useState<number | undefined>(undefined);
  const [openId, setOpenId] = useState<number | null>(null); // 详情抽屉的 submissionId

  // 跨作业切换时重置页码，避免「空表 + 页码超出」。
  useEffect(() => {
    setPage(1);
  }, [assignmentId]);

  // 筛选数据源：成绩册一次给出题目列（assignmentProblemId）与学生名单，与成绩册页共享缓存。
  const gradebookQuery = useQuery({
    queryKey: ['gradebook', assignmentId],
    queryFn: () =>
      client
        .get<ApiResponse<GradebookView>>(`/assignments/${assignmentId}/gradebook`)
        .then((r) => r.data.data),
    enabled: !!assignmentId,
  });
  const gradebook = gradebookQuery.data;

  const listQuery = useQuery({
    queryKey: ['teacher-submissions', assignmentId, page, size, apId, studentId],
    queryFn: () =>
      client
        .get<ApiResponse<Page<TeacherSubmissionRow>>>(`/assignments/${assignmentId}/submissions`, {
          params: { page, size, assignmentProblemId: apId, studentId },
        })
        .then((r) => r.data.data),
    enabled: !!assignmentId,
  });

  // 详情抽屉：打开时按 submissionId 拉取（关闭即禁用）。
  const detailQuery = useQuery({
    queryKey: ['teacher-submission-detail', assignmentId, openId],
    queryFn: () =>
      client
        .get<ApiResponse<TeacherSubmissionDetail>>(
          `/assignments/${assignmentId}/submissions/${openId}`,
        )
        .then((r) => r.data.data),
    enabled: openId != null,
  });
  const detail = openId != null ? detailQuery.data : undefined;

  useEffect(() => {
    if (gradebookQuery.isError) {
      message.error(extractApiMessage(gradebookQuery.error, '筛选名单加载失败'));
    }
  }, [gradebookQuery.isError, gradebookQuery.error, message]);

  useEffect(() => {
    if (listQuery.isError) {
      message.error(extractApiMessage(listQuery.error, '提交历史加载失败'));
    }
  }, [listQuery.isError, listQuery.error, message]);

  useEffect(() => {
    if (detailQuery.isError) {
      message.error(extractApiMessage(detailQuery.error, '提交详情加载失败'));
    }
  }, [detailQuery.isError, detailQuery.error, message]);

  const columns: TableProps<TeacherSubmissionRow>['columns'] = [
    {
      title: '学号',
      dataIndex: 'studentNo',
      key: 'studentNo',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '姓名',
      dataIndex: 'studentName',
      key: 'studentName',
      render: (v: string | null) => v ?? '—',
    },
    {
      title: '题目',
      dataIndex: 'problemTitle',
      key: 'problemTitle',
      render: (v: string | null) => v ?? '—',
    },
    { title: '语言', dataIndex: 'language', key: 'language' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <StatusTag status={s} />,
    },
    {
      title: '得分',
      dataIndex: 'score',
      key: 'score',
      render: (v: number | null, row) =>
        v == null ? '—' : `${v}（通过 ${row.passedCount ?? 0}/${row.totalCount ?? 0} 点）`,
    },
    {
      title: '用时/内存',
      key: 'cost',
      render: (_, row) =>
        row.timeUsedMs == null && row.memoryUsedMb == null
          ? '—'
          : `${row.timeUsedMs ?? '—'} ms / ${row.memoryUsedMb ?? '—'} MB`,
    },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (v: string, row) => (
        <Space size={4}>
          <span>{dayjs(v).format('YYYY-MM-DD HH:mm')}</span>
          {row.isLate && <Tag color="warning">迟交</Tag>}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, row) => (
        <Button size="small" type="text" onClick={() => setOpenId(row.submissionId)}>
          查看
        </Button>
      ),
    },
  ];

  const maskedColumns: TableProps<MaskedPoint>['columns'] = [
    { title: '点位', dataIndex: 'pointIndex', key: 'pointIndex', render: (v: number) => `#${v}` },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <StatusTag status={s} />,
    },
    { title: '用时', dataIndex: 'timeUsedMs', key: 'timeUsedMs', render: (v: number | null) => (v == null ? '—' : `${v} ms`) },
    { title: '内存', dataIndex: 'memoryUsedMb', key: 'memoryUsedMb', render: (v: number | null) => (v == null ? '—' : `${v} MB`) },
  ];

  function renderSamplePoint(p: SamplePoint) {
    const ac = p.status === 'AC';
    return (
      <div
        key={p.pointIndex}
        style={{
          border: `1px solid ${ac ? '#b7eb8f' : '#f0f0f0'}`,
          borderRadius: 6,
          background: ac ? '#f6ffed' : undefined,
          padding: 12,
          marginBottom: 8,
        }}
      >
        <Space size={8}>
          <Typography.Text strong>样例点 #{p.pointIndex}</Typography.Text>
          <StatusTag status={p.status} />
          {p.timeUsedMs != null && <span>{p.timeUsedMs} ms</span>}
          {p.memoryUsedMb != null && <span>{p.memoryUsedMb} MB</span>}
        </Space>
        <div>
          <Typography.Text type="secondary">输入</Typography.Text>
          <pre style={{ margin: '4px 0', whiteSpace: 'pre-wrap' }}>{p.input ?? '（不可用）'}</pre>
          <Typography.Text type="secondary">期望输出</Typography.Text>
          <pre style={{ margin: '4px 0', whiteSpace: 'pre-wrap' }}>
            {p.expectedOutput ?? '（不可用）'}
          </pre>
          {/* message 仅失败点有值（如 RE 的 stderr 文案），AC 点为 null 不渲染 */}
          {p.message && (
            <>
              <Typography.Text type="secondary">错误信息</Typography.Text>
              <pre style={{ margin: '4px 0', whiteSpace: 'pre-wrap', color: '#cf1322' }}>
                {p.message}
              </pre>
            </>
          )}
        </div>
      </div>
    );
  }

  return (
    <Card
      title={
        <Space>
          <Link to={`/teacher/assignments/${assignmentId}`}>
            <Button size="small" type="text" icon={<ArrowLeftOutlined />}>
              返回作业详情
            </Button>
          </Link>
          <span>提交历史{gradebook ? ` · ${gradebook.assignmentTitle}` : ''}</span>
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Select<number | undefined>
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="全部题目"
          style={{ width: 220 }}
          value={apId}
          onChange={(v) => {
            setApId(v ?? undefined);
            setPage(1);
          }}
          options={(gradebook?.problems ?? []).map((p) => ({
            value: p.assignmentProblemId,
            label: p.title,
          }))}
        />
        <Select<number | undefined>
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="全部学生"
          style={{ width: 180 }}
          value={studentId}
          onChange={(v) => {
            setStudentId(v ?? undefined);
            setPage(1);
          }}
          options={(gradebook?.students ?? []).map((s) => ({
            value: s.studentId,
            label: `${s.studentNo} ${s.realName}`,
          }))}
        />
      </Space>

      <Table<TeacherSubmissionRow>
        rowKey="submissionId"
        columns={columns}
        dataSource={listQuery.data?.records ?? []}
        loading={listQuery.isPending}
        pagination={{
          current: page,
          pageSize: size,
          total: listQuery.data?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (t) => `共 ${t} 次提交`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <Drawer
        title={openId != null ? `提交 #${openId} 详情` : '提交详情'}
        open={openId != null}
        onClose={() => setOpenId(null)}
        width={720}
        destroyOnHidden
      >
        {detailQuery.isPending && <Spin />}
        {detail && (
          <>
            <Space size={8} style={{ marginBottom: 8 }}>
              <StatusTag status={detail.status} />
              <Typography.Text type="secondary">语言：{detail.language}</Typography.Text>
            </Space>
            {/* Monaco 只读渲染学生代码；语言键不在映射内时退化为纯文本 */}
            <Editor
              height="340px"
              theme="vs"
              language={MONACO_LANG[detail.language] ?? 'plaintext'}
              value={detail.code}
              options={{ readOnly: true, minimap: { enabled: false } }}
            />
            <Typography.Title level={5} style={{ margin: '16px 0 8px' }}>
              判题明细
            </Typography.Title>
            {detail.samples.length > 0 ? (
              detail.samples.map(renderSamplePoint)
            ) : (
              <Typography.Text type="secondary">无样例点明细（如编译失败）</Typography.Text>
            )}
            {detail.maskedPoints.length > 0 && (
              <>
                <Typography.Title level={5} style={{ margin: '16px 0 8px' }}>
                  隐藏点（共 {detail.maskedPoints.length} 个，仅状态与耗时）
                </Typography.Title>
                <Table<MaskedPoint>
                  rowKey="pointIndex"
                  size="small"
                  pagination={false}
                  columns={maskedColumns}
                  dataSource={detail.maskedPoints}
                />
              </>
            )}
          </>
        )}
      </Drawer>
    </Card>
  );
}
