import { useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Form,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Upload,
} from 'antd';
import type { TableProps } from 'antd';
import type { UploadProps } from 'antd';
import { PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { client, extractApiMessage } from '../../api/client';
import type { ApiResponse, EnrollmentView, ImportResult, Page } from '../../api/types';
import { useTeacherCourse } from './context';
import CourseBreadcrumb from './CourseBreadcrumb';

interface AddStudentForm {
  studentNo: string;
  studentName: string;
}

/** 成员状态展示映射（后端取值：ACTIVE / PENDING）。 */
const STATUS_META: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '已激活', color: 'success' },
  PENDING: { label: '待激活', color: 'warning' },
};

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '已激活' },
  { value: 'PENDING', label: '待激活' },
];

/** 课程学生名单页：分页列表 + 筛选 + 单人添加 + Excel 批量导入 + 移除。 */
export default function CourseStudentsPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();
  const course = useTeacherCourse(courseId);

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [importResult, setImportResult] = useState<ImportResult | null>(null);
  const [form] = Form.useForm<AddStudentForm>();

  const studentsQuery = useQuery({
    queryKey: ['students', courseId, page, size, keyword, status],
    queryFn: () =>
      client
        .get<ApiResponse<Page<EnrollmentView>>>(`/courses/${courseId}/students`, {
          params: { page, size, keyword: keyword || undefined, status: status || undefined },
        })
        .then((r) => r.data.data),
    enabled: !!courseId,
  });

  // 加载失败统一走 message 提示，不裸抛堆栈。
  useEffect(() => {
    if (studentsQuery.isError) {
      message.error(extractApiMessage(studentsQuery.error, '学生名单加载失败'));
    }
  }, [studentsQuery.isError, studentsQuery.error, message]);

  function invalidateStudents() {
    // 前缀失效：名单主体与导入引起的变更一并刷新。
    queryClient.invalidateQueries({ queryKey: ['students', courseId] });
  }

  async function onAddFinish(values: AddStudentForm) {
    setSaving(true);
    try {
      await client.post(`/courses/${courseId}/students`, values);
      message.success('学生已添加');
      setAddOpen(false);
      invalidateStudents();
    } catch (err) {
      message.error(extractApiMessage(err, '添加失败，请重试'));
    } finally {
      setSaving(false);
    }
  }

  async function onDelete(enrollment: EnrollmentView) {
    try {
      await client.delete(`/courses/${courseId}/students/${enrollment.id}`);
      message.success('已移除');
      invalidateStudents();
    } catch (err) {
      message.error(extractApiMessage(err, '移除失败，请重试'));
    }
  }

  // Content-Type 交由浏览器/axios 按 multipart 边界自动设置，不手动写死。
  const importProps: UploadProps = {
    accept: '.xlsx',
    showUploadList: false,
    customRequest: (options) => {
      const fd = new FormData();
      fd.append('file', options.file as Blob);
      client
        .post<ApiResponse<ImportResult>>(`/courses/${courseId}/students/import`, fd)
        .then((r) => {
          options.onSuccess?.(r.data.data);
          setImportResult(r.data.data);
          invalidateStudents();
        })
        .catch((err) => {
          options.onError?.(err as Error);
          message.error(extractApiMessage(err, '导入失败，请重试'));
        });
    },
  };

  const columns: TableProps<EnrollmentView>['columns'] = [
    { title: '学号', dataIndex: 'studentNo', key: 'studentNo' },
    { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => {
        const meta = STATUS_META[s];
        return meta ? <Tag color={meta.color}>{meta.label}</Tag> : <Tag>{s}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      render: (_, enrollment) => (
        <Popconfirm title="确定移除该学生吗？" onConfirm={() => onDelete(enrollment)}>
          <Button size="small" type="text" danger>
            移除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card
      title={course?.name ? `${course.name} · 学生名单` : '课程学生名单'}
      extra={
        <Space>
          <Upload {...importProps}>
            <Button icon={<UploadOutlined />}>导入 Excel</Button>
          </Upload>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              form.resetFields();
              setAddOpen(true);
            }}
          >
            单个添加
          </Button>
        </Space>
      }
    >
      <CourseBreadcrumb courseId={Number(courseId)} courseName={course?.name} current="学生名单" />
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="搜索学号或姓名"
          allowClear
          style={{ width: 240 }}
          onSearch={(v) => {
            setKeyword(v.trim());
            setPage(1);
          }}
        />
        <Select
          value={status}
          options={STATUS_OPTIONS}
          style={{ width: 140 }}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
        />
      </Space>

      <Table<EnrollmentView>
        rowKey="id"
        columns={columns}
        dataSource={studentsQuery.data?.records ?? []}
        loading={studentsQuery.isPending}
        pagination={{
          current: page,
          pageSize: size,
          total: studentsQuery.data?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50],
          showTotal: (t) => `共 ${t} 名学生`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <Modal
        title="单个添加学生"
        open={addOpen}
        onCancel={() => setAddOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText="添加"
        destroyOnHidden
      >
        <Form<AddStudentForm> layout="vertical" form={form} onFinish={onAddFinish}>
          <Form.Item
            label="学号"
            name="studentNo"
            rules={[
              { required: true, message: '请输入学号' },
              { pattern: /^\w{3,30}$/, message: '学号须为3~30位字母/数字/下划线' },
            ]}
          >
            <Input placeholder="3~30位字母/数字/下划线" />
          </Form.Item>
          <Form.Item
            label="姓名"
            name="studentName"
            rules={[{ required: true, message: '请输入姓名' }, { max: 50, message: '姓名不超过50字' }]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="导入结果"
        open={!!importResult}
        onCancel={() => setImportResult(null)}
        footer={
          <Button type="primary" onClick={() => setImportResult(null)}>
            知道了
          </Button>
        }
      >
        {importResult && (
          <>
            <p>
              共 {importResult.totalRows} 行：已激活 {importResult.activated}，待激活{' '}
              {importResult.pending}，重复跳过 {importResult.skipped}，失败{' '}
              {importResult.failures.length}。
            </p>
            {importResult.failures.length > 0 && (
              <List
                size="small"
                header="失败明细"
                dataSource={importResult.failures}
                renderItem={(f) => <List.Item>第 {f.row} 行：{f.reason}</List.Item>}
              />
            )}
          </>
        )}
      </Modal>
    </Card>
  );
}
