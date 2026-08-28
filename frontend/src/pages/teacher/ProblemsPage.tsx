import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tabs,
} from 'antd';
import type { TableProps } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { client, extractApiMessage } from '../../api/client';
import type { ApiResponse, Page, ProblemDetailView, ProblemSummaryView } from '../../api/types';
import MarkdownView from '../../components/MarkdownView';

/** 后端语言键白名单（Languages.ALL）的中文展示。 */
const LANGUAGE_LABELS: Record<string, string> = { C: 'C', CPP: 'C++', JAVA: 'Java', PYTHON: 'Python' };
const LANGUAGE_OPTIONS = Object.entries(LANGUAGE_LABELS).map(([value, label]) => ({ value, label }));
const ALL_LANGUAGE_KEYS = Object.keys(LANGUAGE_LABELS);

/** 与后端 ProblemUpsertRequest 对齐的表单值；缺省交由后端兜底（全语言/1000ms/256MB）。 */
interface ProblemForm {
  title: string;
  description: string;
  allowedLanguages?: string[];
  timeLimitMs?: number;
  memoryLimitMb?: number;
  isPublic?: boolean;
}

/** 题库管理页：分页列表 + 搜索 + 新建/编辑/删除。 */
export default function ProblemsPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ProblemSummaryView | null>(null); // null=新建，非空=编辑该题
  const [editLoading, setEditLoading] = useState(false); // 编辑前拉详情（列表行不含题干）
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<ProblemForm>();
  const description = Form.useWatch('description', form);

  const problemsQuery = useQuery({
    queryKey: ['problems', page, size, keyword],
    queryFn: () =>
      client
        .get<ApiResponse<Page<ProblemSummaryView>>>('/problems', {
          params: { page, size, keyword: keyword || undefined },
        })
        .then((r) => r.data.data),
  });

  // 加载失败统一走 message 提示，不裸抛堆栈。
  useEffect(() => {
    if (problemsQuery.isError) {
      message.error(extractApiMessage(problemsQuery.error, '题库列表加载失败'));
    }
  }, [problemsQuery.isError, problemsQuery.error, message]);

  function invalidateProblems() {
    queryClient.invalidateQueries({ queryKey: ['problems'] });
  }

  function openCreate() {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  }

  // 列表行不含题干，编辑前先拉详情回填表单。
  async function openEdit(problem: ProblemSummaryView) {
    setEditLoading(true);
    try {
      const detail = await client
        .get<ApiResponse<ProblemDetailView>>(`/problems/${problem.id}`)
        .then((r) => r.data.data);
      setEditing(problem);
      form.setFieldsValue({
        title: detail.title,
        description: detail.description,
        allowedLanguages: detail.languages,
        timeLimitMs: detail.timeLimitMs,
        memoryLimitMb: detail.memoryLimitMb,
        isPublic: detail.isPublic,
      });
      setModalOpen(true);
    } catch (err) {
      message.error(extractApiMessage(err, '题目详情加载失败'));
    } finally {
      setEditLoading(false);
    }
  }

  async function onFinish(values: ProblemForm) {
    setSaving(true);
    // 与后端 ProblemUpsertRequest 字段一一对齐。
    const payload = {
      title: values.title,
      description: values.description,
      allowedLanguages: values.allowedLanguages ?? [],
      timeLimitMs: values.timeLimitMs,
      memoryLimitMb: values.memoryLimitMb,
      isPublic: values.isPublic ?? false,
    };
    try {
      if (editing) {
        await client.put(`/problems/${editing.id}`, payload);
        message.success('题目已更新');
      } else {
        await client.post('/problems', payload);
        message.success('题目创建成功');
      }
      setModalOpen(false);
      invalidateProblems();
    } catch (err) {
      message.error(extractApiMessage(err, '保存失败，请重试'));
    } finally {
      setSaving(false);
    }
  }

  async function onDelete(problem: ProblemSummaryView) {
    try {
      await client.delete(`/problems/${problem.id}`);
      message.success('题目已删除');
      invalidateProblems();
    } catch (err) {
      message.error(extractApiMessage(err, '删除失败，请重试'));
    }
  }

  const columns: TableProps<ProblemSummaryView>['columns'] = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      render: (_, problem) => <Link to={`/teacher/problems/${problem.id}`}>{problem.title}</Link>,
    },
    {
      title: '语言',
      dataIndex: 'languages',
      key: 'languages',
      render: (langs: string[]) =>
        langs.map((l) => <Tag key={l}>{LANGUAGE_LABELS[l] ?? l}</Tag>),
    },
    { title: '限时', dataIndex: 'timeLimitMs', key: 'timeLimitMs', render: (v: number) => `${v} ms` },
    { title: '内存', dataIndex: 'memoryLimitMb', key: 'memoryLimitMb', render: (v: number) => `${v} MB` },
    {
      title: '公开',
      dataIndex: 'isPublic',
      key: 'isPublic',
      render: (pub: boolean) =>
        pub ? <Tag color="success">公开</Tag> : <Tag>私有</Tag>, // 只读展示；修改走编辑 Modal
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => (v ? v.replace('T', ' ').slice(0, 16) : '—'),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, problem) => (
        <Space>
          <Button size="small" type="text" loading={editLoading} onClick={() => openEdit(problem)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该题目吗？" onConfirm={() => onDelete(problem)}>
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
      title="题库管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建题目
        </Button>
      }
    >
      <Input.Search
        placeholder="按标题搜索"
        allowClear
        style={{ width: 240, marginBottom: 16 }}
        onSearch={(v) => {
          setKeyword(v.trim());
          setPage(1);
        }}
      />

      <Table<ProblemSummaryView>
        rowKey="id"
        columns={columns}
        dataSource={problemsQuery.data?.records ?? []}
        loading={problemsQuery.isPending}
        pagination={{
          current: page,
          pageSize: size,
          total: problemsQuery.data?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (t) => `共 ${t} 道题`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <Modal
        title={editing ? '编辑题目' : '新建题目'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editing ? '保存' : '创建'}
        width={720}
        destroyOnHidden
      >
        <Form<ProblemForm>
          layout="vertical"
          form={form}
          initialValues={{
            allowedLanguages: ALL_LANGUAGE_KEYS,
            timeLimitMs: 1000,
            memoryLimitMb: 256,
            isPublic: false,
          }}
          onFinish={onFinish}
        >
          <Form.Item
            label="标题"
            name="title"
            rules={[{ required: true, message: '请输入标题' }, { max: 200, message: '标题不超过200字' }]}
          >
            <Input placeholder="如：两数之和" />
          </Form.Item>
          {/* 编辑/预览共用 renderMarkdown；默认停在编辑页签，TextArea 保持挂载不丢校验。 */}
          <Form.Item label="题干（支持 Markdown）" required style={{ marginBottom: 0 }}>
            <Tabs
              defaultActiveKey="edit"
              items={[
                {
                  key: 'edit',
                  label: '编辑',
                  children: (
                    <Form.Item
                      name="description"
                      rules={[{ required: true, message: '请输入题干' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input.TextArea rows={8} placeholder="支持 Markdown：**加粗**、代码块、列表等" />
                    </Form.Item>
                  ),
                },
                {
                  key: 'preview',
                  label: '预览',
                  children: <MarkdownView source={description ?? ''} />,
                },
              ]}
            />
          </Form.Item>
          <Form.Item label="允许语言（不选=全部）" name="allowedLanguages">
            <Select mode="multiple" options={LANGUAGE_OPTIONS} placeholder="不选表示全部语言" />
          </Form.Item>
          <Space size="large">
            <Form.Item
              label="限时（ms）"
              name="timeLimitMs"
              rules={[{ required: true, message: '请输入限时' }]}
            >
              <InputNumber min={1} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item
              label="内存（MB）"
              name="memoryLimitMb"
              rules={[{ required: true, message: '请输入内存限制' }]}
            >
              <InputNumber min={1} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item label="公开" name="isPublic" valuePropName="checked">
              <Switch checkedChildren="公开" unCheckedChildren="私有" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  );
}
