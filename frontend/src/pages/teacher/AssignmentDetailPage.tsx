import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { client, extractApiMessage } from '../../api/client';
import type {
  ApiResponse,
  AssignmentProblemItem,
  AssignmentTeacherDetail,
  CourseProblemView,
  Page,
} from '../../api/types';
import CourseBreadcrumb from './CourseBreadcrumb';

/** 后端 AssignmentStatus 实时推算状态的中文展示。 */
const STATUS_META: Record<string, { label: string; color?: string }> = {
  NOT_STARTED: { label: '未开始' },
  IN_PROGRESS: { label: '进行中', color: 'processing' },
  LATE_WINDOW: { label: '宽限期', color: 'warning' },
  CLOSED: { label: '已结束' },
};

/** 作业详情页（选题计分）：已选题目改分/移除 + 批量选入课程选题。 */
export default function AssignmentDetailPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>();
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [addOpen, setAddOpen] = useState(false);
  const [addPage, setAddPage] = useState(1);
  const [addSize, setAddSize] = useState(10);
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [itemScores, setItemScores] = useState<Record<number, number>>({}); // 新选题配分，缺省 100
  const [adding, setAdding] = useState(false);
  const [scoreDrafts, setScoreDrafts] = useState<Record<number, number>>({}); // 已选题改分草稿
  const [savingScoreId, setSavingScoreId] = useState<number | null>(null);
  const [removingId, setRemovingId] = useState<number | null>(null);

  // 教师详情已内嵌已选题目清单（学生专用列表端点对教师有选课门，不可用）。
  const detailQuery = useQuery({
    queryKey: ['assignment', assignmentId],
    queryFn: () =>
      client
        .get<ApiResponse<AssignmentTeacherDetail>>(`/assignments/${assignmentId}`)
        .then((r) => r.data.data),
    enabled: !!assignmentId,
  });

  const detail = detailQuery.data;

  // 可选题范围=本课程 course_problem（addProblems 的合法范围），Modal 打开时才拉。
  const courseProblemsQuery = useQuery({
    queryKey: ['courseProblems', detail?.courseId, addPage, addSize],
    queryFn: () =>
      client
        .get<ApiResponse<Page<CourseProblemView>>>(`/courses/${detail?.courseId}/problems`, {
          params: { page: addPage, size: addSize },
        })
        .then((r) => r.data.data),
    enabled: addOpen && !!detail?.courseId,
  });

  useEffect(() => {
    if (detailQuery.isError) {
      message.error(extractApiMessage(detailQuery.error, '作业详情加载失败'));
    }
  }, [detailQuery.isError, detailQuery.error, message]);

  useEffect(() => {
    if (courseProblemsQuery.isError) {
      message.error(extractApiMessage(courseProblemsQuery.error, '课程选题列表加载失败'));
    }
  }, [courseProblemsQuery.isError, courseProblemsQuery.error, message]);

  function invalidateDetail() {
    queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] });
  }

  function openAdd() {
    setSelectedKeys([]);
    setItemScores({});
    setAddPage(1);
    setAddOpen(true);
  }

  // 已选题客户端排除，只展示未选入的课程题目。
  const selectedIds = new Set((detail?.problems ?? []).map((p) => p.problemId));
  const availableProblems = (courseProblemsQuery.data?.records ?? []).filter(
    (p) => !selectedIds.has(p.problemId),
  );

  async function onAddProblems() {
    if (!selectedKeys.length) {
      message.warning('请先勾选要添加的题目');
      return;
    }
    // items 内题目不重复（勾选天然去重）；分值后端约束 1~10000，提交前本地同规则校验。
    const items = selectedKeys.map((k) => {
      const pid = Number(k);
      return { problemId: pid, score: itemScores[pid] ?? 100 };
    });
    if (items.some((it) => !Number.isInteger(it.score) || it.score < 1 || it.score > 10000)) {
      message.error('分值须在1~10000');
      return;
    }
    setAdding(true);
    try {
      await client.post(`/assignments/${assignmentId}/problems`, { items });
      message.success(`已添加 ${items.length} 道题`);
      setAddOpen(false);
      invalidateDetail();
    } catch (err) {
      message.error(extractApiMessage(err, '添加选题失败，请重试'));
    } finally {
      setAdding(false);
    }
  }

  async function onSaveScore(item: AssignmentProblemItem) {
    const score = scoreDrafts[item.problemId];
    if (score == null) return;
    setSavingScoreId(item.problemId);
    try {
      await client.put(`/assignments/${assignmentId}/problems/${item.problemId}`, { score });
      message.success('分值已更新');
      setScoreDrafts((s) => {
        const next = { ...s };
        delete next[item.problemId];
        return next;
      });
      invalidateDetail();
    } catch (err) {
      message.error(extractApiMessage(err, '分值更新失败，请重试'));
    } finally {
      setSavingScoreId(null);
    }
  }

  async function onRemoveProblem(item: AssignmentProblemItem) {
    setRemovingId(item.problemId);
    try {
      await client.delete(`/assignments/${assignmentId}/problems/${item.problemId}`);
      message.success('题目已移出作业');
      invalidateDetail();
    } catch (err) {
      message.error(extractApiMessage(err, '移除失败，请重试'));
    } finally {
      setRemovingId(null);
    }
  }

  const problemColumns: TableProps<AssignmentProblemItem>['columns'] = [
    { title: '#', dataIndex: 'sortOrder', key: 'sortOrder', width: 60 },
    {
      title: '题目',
      dataIndex: 'title',
      key: 'title',
      render: (_, item) => (
        <Link
          to={`/teacher/problems/${item.problemId}`}
          state={{ fromAssignment: { courseId: detail?.courseId, assignmentId } }}
        >
          {item.title}
        </Link>
      ),
    },
    {
      title: '分值',
      key: 'score',
      width: 180,
      render: (_, item) => {
        const draft = scoreDrafts[item.problemId];
        return (
          <Space>
            <InputNumber
              min={1}
              max={10000}
              precision={0}
              style={{ width: 90 }}
              value={draft ?? item.score}
              // 清空即回退原值（删除草稿），避免「清空变 0 → 分值须在1~10000」校验噪声。
              onChange={(v) =>
                setScoreDrafts((s) => {
                  const next = { ...s };
                  if (v == null) delete next[item.problemId];
                  else next[item.problemId] = v;
                  return next;
                })
              }
            />
            <Button
              size="small"
              type="text"
              disabled={draft == null || draft === item.score}
              loading={savingScoreId === item.problemId}
              onClick={() => onSaveScore(item)}
            >
              保存
            </Button>
          </Space>
        );
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_, item) => (
        <Space>
          <Button
            size="small"
            type="link"
            onClick={() => navigate(`/teacher/assignments/${assignmentId}/plagiarism/${item.problemId}`)}
          >
            查重
          </Button>
          <Popconfirm title="确定将该题移出作业吗？" onConfirm={() => onRemoveProblem(item)}>
            <Button size="small" type="text" danger loading={removingId === item.problemId}>
              移除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const addColumns: TableProps<CourseProblemView>['columns'] = [
    { title: '题目', dataIndex: 'title', key: 'title' },
    { title: '测试点', dataIndex: 'testCaseCount', key: 'testCaseCount', width: 80 },
    {
      title: '分值',
      key: 'score',
      width: 120,
      render: (_, p) => (
        <InputNumber
          min={1}
          max={10000}
          precision={0}
          style={{ width: 90 }}
          value={itemScores[p.problemId] ?? 100}
          onChange={(v) => setItemScores((s) => ({ ...s, [p.problemId]: v ?? 100 }))}
        />
      ),
    },
  ];

  return (
    <Card>
      <CourseBreadcrumb
        courseId={detail?.courseId}
        courseName={detail?.courseName}
        current={detail ? `${detail.title} · 题目管理` : '题目管理'}
      />
      {detail && (
        <>
          <Space size="middle" style={{ marginBottom: 8 }}>
            <Typography.Title level={4} style={{ margin: 0 }}>
              {detail.title}
            </Typography.Title>
            <Tag color={STATUS_META[detail.status]?.color}>
              {STATUS_META[detail.status]?.label ?? detail.status}
            </Tag>
            <Tag color={detail.isPublished ? 'success' : 'default'}>
              {detail.isPublished ? '已发布' : '草稿'}
            </Tag>
          </Space>
          {detail.description && (
            <Typography.Paragraph type="secondary" style={{ whiteSpace: 'pre-wrap' }}>
              {detail.description}
            </Typography.Paragraph>
          )}
          {/* Task 6 入口：提交历史 / 成绩册（路由随 Task 6 注册） */}
          <Space style={{ marginBottom: 16 }}>
            <Link to={`/teacher/assignments/${assignmentId}/submissions`}>
              <Button size="small">提交历史</Button>
            </Link>
            <Link to={`/teacher/assignments/${assignmentId}/gradebook`}>
              <Button size="small">成绩册</Button>
            </Link>
          </Space>
        </>
      )}

      <Table<AssignmentProblemItem>
        rowKey="problemId"
        title={() => (
          <Space>
            <span>已选题目（共 {(detail?.problems ?? []).length} 题）</span>
            <Button size="small" type="primary" icon={<PlusOutlined />} onClick={openAdd}>
              添加选题
            </Button>
          </Space>
        )}
        columns={problemColumns}
        dataSource={detail?.problems ?? []}
        loading={detailQuery.isPending}
        pagination={false}
      />

      <Modal
        title="添加选题"
        open={addOpen}
        onCancel={() => setAddOpen(false)}
        onOk={onAddProblems}
        confirmLoading={adding}
        okText="添加"
        width={640}
        destroyOnHidden
      >
        <Table<CourseProblemView>
          rowKey="problemId"
          columns={addColumns}
          dataSource={availableProblems}
          loading={courseProblemsQuery.isPending}
          rowSelection={{ selectedRowKeys: selectedKeys, onChange: setSelectedKeys }}
          pagination={{
            current: addPage,
            pageSize: addSize,
            total: courseProblemsQuery.data?.total ?? 0,
            showSizeChanger: false,
            onChange: (p, s) => {
              setAddPage(p);
              setAddSize(s);
            },
          }}
          size="small"
        />
      </Modal>
    </Card>
  );
}
