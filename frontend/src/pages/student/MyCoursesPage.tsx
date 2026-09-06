import { useQuery } from '@tanstack/react-query';
import { Button, Card, Col, Empty, Row, Spin } from 'antd';
import { useNavigate } from 'react-router-dom';
import { client } from '../../api/client';
import type { MyCourseView } from '../../api/studentTypes';

/** 学生我的课程页：GET /api/courses/my 拉取已选修课程卡片，点击进入课程作业列表。 */
export default function MyCoursesPage() {
  const navigate = useNavigate();

  const { data: courses, isLoading } = useQuery<MyCourseView[]>({
    queryKey: ['myCourses'],
    queryFn: async () => {
      const resp = await client.get('/courses/my');
      return resp.data.data;
    },
  });

  if (isLoading) {
    return <Spin style={{ display: 'block', margin: '48px auto' }} />;
  }

  const list = courses ?? [];
  if (list.length === 0) {
    return (
      <Empty description="你还没有加入任何课程，请联系任课教师导入学号后重新登录。">
        <Button type="primary" onClick={() => navigate('/student/practice/problems')}>
          去自由练习
        </Button>
      </Empty>
    );
  }

  const enterCourse = (courseId: number) => {
    navigate(`/student/courses/${courseId}/assignments`);
  };

  return (
    <div>
      <h2>我的课程</h2>
      <Row gutter={[16, 16]} data-testid="course-grid">
        {list.map((c) => (
          <Col key={c.courseId} xs={24} sm={12} md={8} lg={6}>
            <Card hoverable title={c.name} onClick={() => enterCourse(c.courseId)}>
              <div>学期：{c.term}</div>
              <Button type="link" onClick={() => enterCourse(c.courseId)}>
                查看作业
              </Button>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}