import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import dayjs, { type Dayjs } from 'dayjs';
import { client, extractApiMessage } from '../../api/client';
import type {
  ApiResponse,
  AssignmentTeacherDetail,
  AssignmentTeacherItem,
  Page,
} from '../../api/types';

/** 后端 AssignmentStatus 实时推算状态的中文展示。 */
const STATUS_META: Record<string, { label: string; color?: string }> = {
  NOT_STARTED: { label: '未开始' },
  IN_PROGRESS: { label: '进行中', color: 'processing' },
  LATE_WINDOW: { label: '宽限期', color: 'warning' },
  CLOSED: { label: '已结束' },
};

/** 提交后端的 LocalDateTime 序列化格式（禁 toISOString：UTC 偏移会错位）。 */
const DATETIME_PAYLOAD = 'YYYY-MM-DDTHH:mm:ss';

/** 与后端 AssignmentUpsertRequest 对齐的表单值；lateDays/isPublished 缺省交后端兜底。 */
interface AssignmentForm {
  title: string;
  description?: string;
  startAt: Dayjs;
  dueAt: Dayjs;
  lateDays?: number;
  isPublished?: boolean;
}

/** 作业管理页（课程内）：分页列表 + 新建/编辑/发布开关/删除。 */
export default function AssignmentsPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AssignmentTeacherItem | null>(null); // null=新建
  const [editLoading, setEditLoading] = useState(false); // 列表行无 description，编辑前先拉详情
  const [saving, setSaving] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null); // 发布开关请求中的行
  const [form] = Form.useForm<AssignmentForm>();

  const assignmentsQuery = useQuery({
    queryKey: ['assignments', courseId, page, size],
    queryFn: () =>
      client
        .get<ApiResponse<Page<AssignmentTeacherItem>>>(`/courses/${courseId}/assignments`, {
          params: { page, size },
        })
        .then((r) => r.data.data),
    enabled: !!courseId,
  });

  // 加载失败统一走 message 提示，不裸抛堆栈。
  useEffect(() => {
    if (assignmentsQuery.isError) {
      message.error(extractApiMessage(assignmentsQuery.error, '作业列表加载失败'));
    }
  }, [assignmentsQuery.isError, assignmentsQuery.error, message]);

  function invalidateAssignments() {
    queryClient.invalidateQueries({ queryKey: ['assignments', courseId] });
  }

  function openCreate() {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  }

  // 列表行不含 description，编辑前先拉详情回填表单（同题目编辑模式）。
  async function openEdit(item: AssignmentTeacherItem) {
    setEditLoading(true);
    try {
      const detail = await client
        .get<ApiResponse<AssignmentTeacherDetail>>(`/assignments/${item.id}`)
        .then((r) => r.data.data);
      setEditing(item);
      form.setFieldsValue({
        title: detail.title,
        description: detail.description ?? '',
        startAt: dayjs(detail.startAt),
        dueAt: dayjs(detail.dueAt),
        lateDays: detail.lateDays,
        isPublished: detail.isPublished,
      });
      setModalOpen(true);
    } catch (err) {
      message.error(extractApiMessage(err, '作业详情加载失败'));
    } finally {
      setEditLoading(false);
    }
  }

  async function onFinish(values: AssignmentForm) {
    setSaving(true);
    // 与后端 AssignmentUpsertRequest 字段一一对齐；时间为本地时区 naive 字符串。
    const payload = {
      title: values.title,
      description: values.description ?? null,
      startAt: values.startAt.format(DATETIME_PAYLOAD),
      dueAt: values.dueAt.format(DATETIME_PAYLOAD),
      lateDays: values.lateDays ?? 0,
      isPublished: values.isPublished ?? false,
    };
    try {
      if (editing) {
        await client.put(`/assignments/${editing.id}`, payload);
        message.success('作业已更新');
      } else {
        await client.post(`/courses/${courseId}/assignments`, payload);
        message.success('作业创建成功');
      }
      setModalOpen(false);
      invalidateAssignments();
    } catch (err) {
      message.error(extractApiMessage(err, '保存失败，请重试'));
    } finally {
      setSaving(false);
    }
  }

  // PUT 为全量语义（description 缺省即清空），切换前先拉详情拼完整载荷。
  async function onTogglePublish(item: AssignmentTeacherItem) {
    setTogglingId(item.id);
    try {
      const detail = await client
        .get<ApiResponse<AssignmentTeacherDetail>>(`/assignments/${item.id}`)
        .then((r) => r.data.data);
      await client.put(`/assignments/${item.id}`, {
        title: detail.title,
        description: detail.description,
        startAt: detail.startAt,
        dueAt: detail.dueAt,
        lateDays: detail.lateDays,
        isPublished: !item.isPublished,
      });
      message.success(item.isPublished ? '作业已下线' : '作业已发布');
      invalidateAssignments();
    } catch (err) {
      message.error(extractApiMessage(err, '发布状态更新失败，请重试'));
    } finally {
      setTogglingId(null);
    }
  }

  async function onDelete(item: AssignmentTeacherItem) {
    try {
      await client.delete(`/assignments/${item.id}`);
      message.success('作业已删除');
      invalidateAssignments();
    } catch (err) {
      message.error(extractApiMessage(err, '删除失败，请重试'));
    }
  }

  const columns: TableProps<AssignmentTeacherItem>['columns'] = [
    {
      title: '标题',
      dataIndex: 'title',
      key: 'title',
      render: (_, item) => <Link to={`/teacher/assignments/${item.id}`}>{item.title}</Link>,
    },
    {
      title: '起止时间',
      key: 'time',
      render: (_, item) =>
        `${dayjs(item.startAt).format('YYYY-MM-DD HH:mm')} ~ ${dayjs(item.dueAt).format('YYYY-MM-DD HH:mm')}`,
    },
    { title: '宽限天数', dataIndex: 'lateDays', key: 'lateDays', render: (v: number) => `${v} 天` },
    { title: '题数', dataIndex: 'problemCount', key: 'problemCount' },
    { title: '总分', dataIndex: 'totalScore', key: 'totalScore' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <Tag color={STATUS_META[s]?.color}>{STATUS_META[s]?.label ?? s}</Tag>,
    },
    {
      title: '发布',
      dataIndex: 'isPublished',
      key: 'isPublished',
      render: (pub: boolean, item) => (
        <Popconfirm
          title={pub ? '确定下线该作业吗？' : '确定发布该作业吗？'}
          onConfirm={() => onTogglePublish(item)}
        >
          {/* 受控无 onChange：点击只弹确认，确认后才真正切换 */}
          <Switch
            size="small"
            checked={pub}
            loading={togglingId === item.id}
            checkedChildren="已发布"
            unCheckedChildren="草稿"
          />
        </Popconfirm>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, item) => (
        <Space>
          <Button size="small" type="text" loading={editLoading} onClick={() => openEdit(item)}>
            编辑
          </Button>
          <Button size="small" type="text" onClick={() => navigate(`/teacher/assignments/${item.id}`)}>
            选题
          </Button>
          <Popconfirm title="确定删除该作业吗？" onConfirm={() => onDelete(item)}>
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
      title="作业管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建作业
        </Button>
      }
    >
      <Table<AssignmentTeacherItem>
        rowKey="id"
        columns={columns}
        dataSource={assignmentsQuery.data?.records ?? []}
        loading={assignmentsQuery.isPending}
        pagination={{
          current: page,
          pageSize: size,
          total: assignmentsQuery.data?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (t) => `共 ${t} 个作业`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <Modal
        title={editing ? '编辑作业' : '新建作业'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editing ? '保存' : '创建'}
        destroyOnHidden
      >
        <Form<AssignmentForm>
          layout="vertical"
          form={form}
          initialValues={{ lateDays: 0, isPublished: false }}
          onFinish={onFinish}
        >
          <Form.Item
            label="标题"
            name="title"
            rules={[{ required: true, message: '请输入作业标题' }, { max: 200, message: '标题不超过200字' }]}
          >
            <Input placeholder="如：第一次编程作业" />
          </Form.Item>
          <Form.Item label="作业说明" name="description">
            <Input.TextArea rows={4} placeholder="选填" />
          </Form.Item>
          <Space size="large">
            <Form.Item
              label="开始时间"
              name="startAt"
              rules={[{ required: true, message: '请选择开始时间' }]}
            >
              <DatePicker showTime={{ showSecond: false }} placeholder="开始时间" />
            </Form.Item>
            <Form.Item
              label="结束时间"
              name="dueAt"
              rules={[
                { required: true, message: '请选择结束时间' },
                ({ getFieldValue }) => ({
                  validator(_, value: Dayjs) {
                    const startAt = getFieldValue('startAt') as Dayjs | undefined;
                    if (!value || !startAt || value.isAfter(startAt)) return Promise.resolve();
                    return Promise.reject(new Error('结束时间必须晚于开始时间'));
                  },
                }),
              ]}
            >
              <DatePicker showTime={{ showSecond: false }} placeholder="结束时间" />
            </Form.Item>
          </Space>
          <Space size="large">
            <Form.Item
              label="宽限天数"
              name="lateDays"
              rules={[{ required: true, message: '请输入宽限天数' }]}
            >
              <InputNumber min={0} max={7} precision={0} style={{ width: 120 }} />
            </Form.Item>
            <Form.Item label="立即发布" name="isPublished" valuePropName="checked">
              <Switch checkedChildren="发布" unCheckedChildren="草稿" />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  );
}
