import { useEffect, useState } from 'react';
import { App as AntApp, Button, Card, Form, Input, Modal, Popconfirm, Space, Table } from 'antd';
import type { TableProps } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { client, extractApiMessage } from '../../api/client';
import type { ApiResponse, CourseView, Page } from '../../api/types';

interface CourseForm {
  name: string;
  term: string;
  description?: string;
}

/** 课程管理页：列表 + 新建/编辑/删除。 */
export default function CoursesPage() {
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CourseView | null>(null); // null=新建，非空=编辑该课程
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<CourseForm>();

  const coursesQuery = useQuery({
    queryKey: ['courses'],
    queryFn: () => client.get<ApiResponse<Page<CourseView>>>('/courses').then((r) => r.data.data),
  });

  // 加载失败统一走 message 提示，不裸抛堆栈。
  useEffect(() => {
    if (coursesQuery.isError) {
      message.error(extractApiMessage(coursesQuery.error, '课程列表加载失败'));
    }
  }, [coursesQuery.isError, coursesQuery.error, message]);

  function invalidateCourses() {
    queryClient.invalidateQueries({ queryKey: ['courses'] });
  }

  function openCreate() {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  }

  function openEdit(course: CourseView) {
    setEditing(course);
    form.setFieldsValue({ name: course.name, term: course.term, description: course.description ?? '' });
    setModalOpen(true);
  }

  async function onFinish(values: CourseForm) {
    setSaving(true);
    try {
      if (editing) {
        await client.put(`/courses/${editing.id}`, values);
        message.success('课程已更新');
      } else {
        await client.post('/courses', values);
        message.success('课程创建成功');
      }
      setModalOpen(false);
      invalidateCourses();
    } catch (err) {
      message.error(extractApiMessage(err, '保存失败，请重试'));
    } finally {
      setSaving(false);
    }
  }

  async function onDelete(course: CourseView) {
    try {
      await client.delete(`/courses/${course.id}`);
      message.success('课程已删除');
      invalidateCourses();
    } catch (err) {
      message.error(extractApiMessage(err, '删除失败，请重试'));
    }
  }

  const columns: TableProps<CourseView>['columns'] = [
    {
      title: '课程名称',
      dataIndex: 'name',
      key: 'name',
      render: (_, course) => (
        <Link to={`/teacher/courses/${course.id}/students`}>{course.name}</Link>
      ),
    },
    { title: '学期', dataIndex: 'term', key: 'term' },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (desc: string | null) => desc ?? '—',
    },
    {
      title: '操作',
      key: 'action',
      render: (_, course) => (
        <Space>
          <Button size="small" type="text" onClick={() => openEdit(course)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该课程吗？" onConfirm={() => onDelete(course)}>
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
      title="课程管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建课程
        </Button>
      }
    >
      <Table<CourseView>
        rowKey="id"
        columns={columns}
        dataSource={coursesQuery.data?.records ?? []}
        loading={coursesQuery.isPending}
        pagination={{ showSizeChanger: true }}
      />

      <Modal
        title={editing ? '编辑课程' : '新建课程'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editing ? '保存' : '创建'}
        destroyOnHidden
      >
        <Form<CourseForm> layout="vertical" form={form} onFinish={onFinish}>
          <Form.Item
            label="课程名称"
            name="name"
            rules={[{ required: true, message: '请输入课程名称' }, { max: 100, message: '课程名不超过100字' }]}
          >
            <Input placeholder="如：线性代数" />
          </Form.Item>
          <Form.Item
            label="学期"
            name="term"
            rules={[{ required: true, message: '请输入学期' }, { max: 50, message: '学期不超过50字' }]}
          >
            <Input placeholder="如：2026秋" />
          </Form.Item>
          <Form.Item label="描述" name="description" rules={[{ max: 500, message: '描述不超过500字' }]}>
            <Input.TextArea rows={3} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
