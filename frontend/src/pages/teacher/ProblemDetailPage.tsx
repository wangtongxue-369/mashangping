import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import type { TableProps } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { client, extractApiMessage } from '../../api/client';
import type { ApiResponse, ProblemDetailView, TestCaseView } from '../../api/types';
import MarkdownView from '../../components/MarkdownView';

/** 后端语言键的中文展示（与 ProblemsPage 一致）。 */
const LANGUAGE_LABELS: Record<string, string> = { C: 'C', CPP: 'C++', JAVA: 'Java', PYTHON: 'Python' };

/** 与后端 TestCaseUpsertRequest 对齐的表单值。 */
interface TestCaseForm {
  input: string;
  expectedOutput: string;
  isSample?: boolean;
}

/** 长文本截断摘要：压平空白后取前 max 字符。 */
function toSummary(text: string, max = 40): string {
  const flat = text.replace(/\s+/g, ' ').trim();
  return flat.length > max ? `${flat.slice(0, max)}…` : flat || '—';
}

/** 题目详情页：题干 Markdown 渲染 + 测试点增删改（属主视角，含隐藏点）。 */
export default function ProblemDetailPage() {
  const { problemId } = useParams<{ problemId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  // 来路感知：从「作业详情-题目」进入时返回作业详情；默认（题库进入/深链刷新）返回题库。
  const fromAssignment = (
    location.state as { fromAssignment?: { assignmentId?: number } } | null
  )?.fromAssignment;
  const backLabel = fromAssignment?.assignmentId ? '返回作业' : '返回题库';
  const goBack = () => {
    if (fromAssignment?.assignmentId) {
      navigate(`/teacher/assignments/${fromAssignment.assignmentId}`);
    } else {
      navigate('/teacher/problems');
    }
  };

  const [caseModalOpen, setCaseModalOpen] = useState(false);
  const [editingCase, setEditingCase] = useState<TestCaseView | null>(null); // null=新增，非空=编辑该测试点
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<TestCaseForm>();

  const detailQuery = useQuery({
    queryKey: ['problem-detail', problemId],
    queryFn: () =>
      client
        .get<ApiResponse<ProblemDetailView>>(`/problems/${problemId}`)
        .then((r) => r.data.data),
    enabled: !!problemId,
  });

  // 加载失败统一走 message 提示，不裸抛堆栈。
  useEffect(() => {
    if (detailQuery.isError) {
      message.error(extractApiMessage(detailQuery.error, '题目详情加载失败'));
    }
  }, [detailQuery.isError, detailQuery.error, message]);

  function invalidateDetail() {
    queryClient.invalidateQueries({ queryKey: ['problem-detail', problemId] });
  }

  function openCreateCase() {
    setEditingCase(null);
    form.resetFields();
    setCaseModalOpen(true);
  }

  function openEditCase(tc: TestCaseView) {
    setEditingCase(tc);
    form.setFieldsValue({ input: tc.input, expectedOutput: tc.expectedOutput, isSample: tc.isSample });
    setCaseModalOpen(true);
  }

  async function onCaseFinish(values: TestCaseForm) {
    setSaving(true);
    try {
      const payload = { input: values.input, expectedOutput: values.expectedOutput, isSample: values.isSample ?? false };
      if (editingCase) {
        await client.put(`/problems/${problemId}/test-cases/${editingCase.id}`, payload);
        message.success('测试点已更新');
      } else {
        await client.post(`/problems/${problemId}/test-cases`, payload);
        message.success('测试点已添加');
      }
      setCaseModalOpen(false);
      invalidateDetail();
    } catch (err) {
      message.error(extractApiMessage(err, '保存失败，请重试'));
    } finally {
      setSaving(false);
    }
  }

  async function onDeleteCase(tc: TestCaseView) {
    try {
      await client.delete(`/problems/${problemId}/test-cases/${tc.id}`);
      message.success('测试点已删除');
      invalidateDetail();
    } catch (err) {
      message.error(extractApiMessage(err, '删除失败，请重试'));
    }
  }

  const detail = detailQuery.data;
  const hiddenCount = detail?.testCases.filter((tc) => !tc.isSample).length ?? 0;

  const columns: TableProps<TestCaseView>['columns'] = [
    {
      title: '类型',
      dataIndex: 'isSample',
      key: 'isSample',
      width: 90,
      render: (sample: boolean) => (sample ? <Tag color="success">样例</Tag> : <Tag>隐藏</Tag>),
    },
    {
      title: '输入',
      dataIndex: 'input',
      key: 'input',
      render: (v: string) => (
        <Tooltip title={<pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{v}</pre>}>
          <span style={{ fontFamily: 'monospace' }}>{toSummary(v)}</span>
        </Tooltip>
      ),
    },
    {
      title: '期望输出',
      dataIndex: 'expectedOutput',
      key: 'expectedOutput',
      render: (v: string) => (
        <Tooltip title={<pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{v}</pre>}>
          <span style={{ fontFamily: 'monospace' }}>{toSummary(v)}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, tc) => (
        <Space>
          <Button size="small" type="text" onClick={() => openEditCase(tc)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该测试点吗？" onConfirm={() => onDeleteCase(tc)}>
            <Button size="small" type="text" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={goBack}>
            {backLabel}
          </Button>
          <span>{detail?.title ?? '题目详情'}</span>
          {detail && (detail.isPublic ? <Tag color="success">公开</Tag> : <Tag>私有</Tag>)}
          {hiddenCount > 0 && <Tag color="warning">含隐藏点 {hiddenCount} 个</Tag>}
        </Space>
      }
    >
      {detail && (
        <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
          <Descriptions.Item label="语言">
            <Space size={4} wrap>
              {detail.languages.map((l) => (
                <Tag key={l}>{LANGUAGE_LABELS[l] ?? l}</Tag>
              ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="限制">
            限时 {detail.timeLimitMs} ms · 内存 {detail.memoryLimitMb} MB
          </Descriptions.Item>
          <Descriptions.Item label="测试点">
            {detail.testCases.length} 个（样例 {detail.testCases.length - hiddenCount}）
          </Descriptions.Item>
        </Descriptions>
      )}

      {/* 题干：Markdown 安全渲染（marked + DOMPurify）。 */}
      <MarkdownView source={detail?.description ?? ''} />

      <Button
        type="primary"
        icon={<PlusOutlined />}
        style={{ margin: '24px 0 16px' }}
        onClick={openCreateCase}
        disabled={!detail}
      >
        新增测试点
      </Button>
      <Table<TestCaseView>
        rowKey="id"
        columns={columns}
        dataSource={detail?.testCases ?? []}
        loading={detailQuery.isPending}
        pagination={false}
      />

      <Modal
        title={editingCase ? '编辑测试点' : '新增测试点'}
        open={caseModalOpen}
        onCancel={() => setCaseModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editingCase ? '保存' : '添加'}
        width={640}
        destroyOnHidden
      >
        <Form<TestCaseForm>
          layout="vertical"
          form={form}
          initialValues={{ isSample: true }}
          onFinish={onCaseFinish}
        >
          <Form.Item label="样例点（学生可见）" name="isSample" valuePropName="checked">
            <Switch checkedChildren="样例" unCheckedChildren="隐藏" />
          </Form.Item>
          <Form.Item label="输入" name="input" rules={[{ required: true, message: '请输入测试点输入' }]}>
            <Input.TextArea rows={5} placeholder="程序标准输入，支持多行" />
          </Form.Item>
          <Form.Item
            label="期望输出"
            name="expectedOutput"
            rules={[{ required: true, message: '请输入期望输出' }]}
          >
            <Input.TextArea rows={5} placeholder="与标准输出逐行比对" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
