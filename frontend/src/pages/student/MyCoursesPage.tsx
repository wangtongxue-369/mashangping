import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Empty, Row, Spin, Tag } from 'antd';
import { BookOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { client } from '../../api/client';
import type { MyCourseView } from '../../api/studentTypes';
import { MY_COURSES_KEY } from './context';

/** 学生我的课程页：GET /api/courses/my 拉取已选修课程卡片，点击进入课程作业列表。 */
export default function MyCoursesPage() {
  const navigate = useNavigate();

  const { data: courses, isLoading, isError, refetch } = useQuery<MyCourseView[]>({
    queryKey: MY_COURSES_KEY,
    queryFn: async () => {
      const resp = await client.get('/courses/my');
      return resp.data.data;
    },
  });

  if (isLoading) {
    return <Spin style={{ display: 'block', margin: '48px auto' }} />;
  }

  // H4：接口失败 ≠ 空数据——失败给错误卡 + 重试，不静默渲染「尚未加入课程」。
  if (isError || courses == null) {
    return (
      <div>
        <h2 className="msp-page-title">我的课程</h2>
        <div className="msp-card" style={{ marginTop: 16, textAlign: 'center' }}>
          <Alert type="error" showIcon message="课程列表加载失败，请检查网络后重试" style={{ marginBottom: 12 }} />
          <Button onClick={() => refetch()}>重试</Button>
        </div>
      </div>
    );
  }

  const list = courses;
  if (list.length === 0) {
    return (
      <div>
        <h2 className="msp-page-title">我的课程</h2>
        <p className="msp-page-sub">选择一门课程查看作业与题目</p>
        <div className="msp-card" style={{ marginTop: 16 }}>
          <Empty description="你还没有加入任何课程：请联系任课教师导入学号后重新登录；若你还没有账号，可先在登录页注册学生账号（老师导入过学号后，注册成功即自动加入对应课程）。">
            <Button type="primary" onClick={() => navigate('/student/practice/problems')}>
              去自由练习
            </Button>
          </Empty>
        </div>
      </div>
    );
  }

  const enterCourse = (courseId: number) => {
    navigate(`/student/courses/${courseId}/assignments`);
  };

  return (
    <div>
      <h2 className="msp-page-title">我的课程</h2>
      <p className="msp-page-sub">选择一门课程查看作业与题目</p>
      <Row gutter={[16, 16]}>
        {list.map((c) => (
          <Col key={c.courseId} xs={24} sm={12} md={8} lg={6}>
            <Card
              hoverable
              onClick={() => enterCourse(c.courseId)}
              style={{ borderRadius: 12, border: '1px solid #e6e8eb' }}
              styles={{ body: { padding: '18px 20px' } }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
                <span
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: 8,
                    background: '#e8f2ff',
                    color: '#1677ff',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 17,
                    flex: 'none',
                  }}
                >
                  <BookOutlined />
                </span>
                <div style={{ fontSize: 16, fontWeight: 600, color: '#1f2329', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {c.name}
                </div>
              </div>
              <Tag style={{ marginBottom: 0 }}>{c.term}</Tag>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}
