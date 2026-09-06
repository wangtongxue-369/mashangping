import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Area, Column, Line, Pie } from '@ant-design/plots';
import { Alert, Button, Card, Col, Empty, Row, Select, Spin, Statistic, Tag, Typography } from 'antd';
import { TeamOutlined, FileTextOutlined, TrophyOutlined, WarningOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { client } from '../../api/client';
import type {
  AcRateRow,
  CourseOverview,
  PlagiarismRiskRow,
  ScoreDistribution,
  ScorePoint,
  StatusCount,
  TimelinePoint,
} from '../../api/analyticsTypes';
import { useTeacherCourse } from './context';
import CourseBreadcrumb from './CourseBreadcrumb';

const STATUS_LABEL: Record<string, string> = {
  AC: '通过', WA: '答案错误', TLE: '超时', MLE: '内存超限',
  RE: '运行时错误', CE: '编译错误', SYSTEM_ERROR: '系统错误', PENDING: '排队中', RUNNING: '评测中',
};

function fetchList<T>(path: string) {
  return async (): Promise<T> => {
    const resp = await client.get(path);
    return resp.data.data;
  };
}

function SectionCard({ title, extra, children }: { title: string; extra?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="msp-card" style={{ marginBottom: 16, padding: '16px 20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Typography.Title level={5} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        {extra}
      </div>
      {children}
    </div>
  );
}

/** 课程工作台：概览 + 图形看板 + 业务入口（教师课程二级界面）。 */
export default function CourseConsolePage() {
  const { courseId } = useParams<{ courseId: string }>();
  const navigate = useNavigate();
  const course = useTeacherCourse(courseId);
  const [distAssignmentId, setDistAssignmentId] = useState<number | null>(null);

  const overview = useQuery<CourseOverview>({
    queryKey: ['courseOverview', courseId],
    queryFn: fetchList(`/courses/${courseId}/analytics/overview`),
    enabled: Boolean(courseId),
  });
  const trend = useQuery<ScorePoint[]>({
    queryKey: ['scoreTrend', courseId],
    queryFn: fetchList(`/courses/${courseId}/analytics/score-trend`),
    enabled: Boolean(courseId),
  });
  const acRate = useQuery<AcRateRow[]>({
    queryKey: ['acRate', courseId],
    queryFn: fetchList(`/courses/${courseId}/analytics/ac-rate`),
    enabled: Boolean(courseId),
  });
  const status = useQuery<StatusCount[]>({
    queryKey: ['statusDist', courseId],
    queryFn: fetchList(`/courses/${courseId}/analytics/status-distribution`),
    enabled: Boolean(courseId),
  });
  const timeline = useQuery<TimelinePoint[]>({
    queryKey: ['timeline', courseId],
    queryFn: fetchList(`/courses/${courseId}/analytics/submission-timeline`),
    enabled: Boolean(courseId),
  });
  const risk = useQuery<PlagiarismRiskRow[]>({
    queryKey: ['plagRisk', courseId],
    queryFn: fetchList(`/courses/${courseId}/analytics/plagiarism-risk`),
    enabled: Boolean(courseId),
  });

  const chosenAssignmentId = distAssignmentId ?? trend.data?.[0]?.assignmentId ?? null;
  const distribution = useQuery<ScoreDistribution>({
    queryKey: ['scoreDist', chosenAssignmentId],
    queryFn: fetchList(`/assignments/${chosenAssignmentId}/analytics/score-distribution`),
    enabled: chosenAssignmentId != null,
  });

  const ov = overview.data;
  const loading = overview.isLoading || trend.isLoading;

  // H4：接口失败 ≠ 空数据——任一分析查询失败则顶部警示并提供重试，不静默渲染误导性的全 0 看板。
  const failedQueries = [overview, trend, acRate, status, timeline, risk, distribution].filter(
    (q) => q.isError,
  );
  // 概览卡缺数时取「—」（区别于真实 0）：接口失败与无数据不再混为一谈。
  const ovNum = (v: number | undefined | null) => (ov ? (v ?? 0) : overview.isError ? '—' : 0);

  return (
    <div>
      <CourseBreadcrumb courseId={Number(courseId)} courseName={course?.name} current="课程工作台" />
      <h2 className="msp-page-title">{course?.name ? `${course.name} · 课程工作台` : '课程工作台'}</h2>
      <p className="msp-page-sub">
        课程数据看板与管理入口{course?.term ? ` · ${course.term}` : ''}
      </p>

      {failedQueries.length > 0 ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message={`部分看板数据加载失败（${failedQueries.length} 项），对应图表已隐藏，请重试`}
          action={
            <Button size="small" onClick={() => failedQueries.forEach((q) => q.refetch())}>
              重试
            </Button>
          }
        />
      ) : null}

      {loading ? (
        <Spin style={{ display: 'block', margin: '48px auto' }} />
      ) : (
        <>
          {/* 概览卡 */}
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <StatCard icon={<TeamOutlined />} label="在册学生" value={ovNum(ov?.studentCount)} />
            <StatCard icon={<FileTextOutlined />} label="作业 / 已发布"
              value={`${ovNum(ov?.assignmentCount)} / ${ovNum(ov?.publishedAssignmentCount)}`} />
            <StatCard icon={<TrophyOutlined />} label="最近作业平均分"
              value={ov && ov.latestAvg != null ? String(ov.latestAvg) : '—'} />
            <StatCard icon={<WarningOutlined />} label="查重高疑似对"
              value={ovNum(ov?.highPlagiarismPairs)} danger={(ov?.highPlagiarismPairs ?? 0) > 0} />
          </Row>

          {/* 业务入口 */}
          <SectionCard title="管理入口">
            <Row gutter={[16, 16]}>
              <Entry label="学生名单" desc="花名册 / 导入 / 移除" onClick={() => navigate(`/teacher/courses/${courseId}/students`)} />
              <Entry label="作业管理" desc="发布 / 选题 / 成绩册 / 查重" onClick={() => navigate(`/teacher/courses/${courseId}/assignments`)} />
              <Entry
                label="最近作业详情"
                desc="题目与成绩册入口"
                disabled={!trend.data?.length}
                onClick={() => trend.data?.[0] && navigate(`/teacher/assignments/${trend.data[0].assignmentId}`)}
              />
            </Row>
          </SectionCard>

          {/* 成绩趋势 */}
          <SectionCard title="各作业成绩趋势（平均 / 最高 / 最低）">
            {trend.data && trend.data.length ? (
              <Line
                data={trend.data.flatMap((p) => [
                  { 作业: p.title, 分数: p.avg, 系列: '平均' },
                  { 作业: p.title, 分数: p.max, 系列: '最高' },
                  { 作业: p.title, 分数: p.min, 系列: '最低' },
                ])}
                xField="作业"
                yField="分数"
                colorField="系列"
                height={260}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布作业" />
            )}
          </SectionCard>

          {/* 分数段分布 */}
          <SectionCard
            title="作业分数段分布"
            extra={
              trend.data && trend.data.length ? (
                <Select
                  size="small"
                  style={{ width: 220 }}
                  value={chosenAssignmentId}
                  onChange={(v: number) => setDistAssignmentId(v)}
                  options={trend.data.map((p) => ({ value: p.assignmentId, label: p.title }))}
                />
              ) : undefined
            }
          >
            {distribution.data ? (
              <Column
                data={distribution.data.buckets.map((b) => ({ 分数段: b.label, 人数: b.count }))}
                xField="分数段"
                yField="人数"
                height={240}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无分数段数据" />
            )}
          </SectionCard>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={14}>
              <SectionCard title="每题 AC 率（已发布作业）">
                {acRate.data && acRate.data.length ? (
                  <Column
                    data={acRate.data.map((r) => ({
                      题目: `${r.assignmentTitle} · ${r.problemTitle}`,
                      通过率: Math.round(r.rate * 100),
                    }))}
                    xField="题目"
                    yField="通过率"
                    height={260}
                    axis={{ x: { label: { autoRotate: true } } }}
                  />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 AC 率数据" />
                )}
              </SectionCard>
            </Col>
            <Col xs={24} lg={10}>
              <SectionCard title="提交状态分布">
                {status.data && status.data.length ? (
                  <Pie
                    data={status.data.map((s) => ({ 状态: STATUS_LABEL[s.status] ?? s.status, 数量: s.count }))}
                    angleField="数量"
                    colorField="状态"
                    height={260}
                  />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无提交" />
                )}
              </SectionCard>
            </Col>
          </Row>

          <SectionCard title="提交活跃时间线">
            {timeline.data && timeline.data.length ? (
              <Area
                data={timeline.data.map((t) => ({ 日期: t.date, 提交量: t.count }))}
                xField="日期"
                yField="提交量"
                height={220}
              />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无提交记录" />
            )}
          </SectionCard>

          <SectionCard title="查重风险汇总">
            {risk.data && risk.data.length ? (
              <div>
                {risk.data.map((r) => (
                  <div
                    key={`${r.assignmentId}-${r.problemId}`}
                    onClick={() =>
                      navigate(`/teacher/assignments/${r.assignmentId}/plagiarism/${r.problemId}`)
                    }
                    style={{
                      display: 'flex', alignItems: 'center', gap: 12, padding: '8px 4px',
                      cursor: 'pointer', borderBottom: '1px solid #f0f0f0',
                    }}
                  >
                    <span style={{ flex: 1 }}>
                      {r.assignmentTitle} · {r.problemTitle}
                    </span>
                    {r.highPairs > 0 ? <Tag color="red">高疑似 {r.highPairs}</Tag> : null}
                    {r.midPairs > 0 ? <Tag color="orange">关注 {r.midPairs}</Tag> : null}
                    <span style={{ color: '#9aa0a6' }}>→</span>
                  </div>
                ))}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前无高疑似/关注的查重对" />
            )}
          </SectionCard>
        </>
      )}
    </div>
  );
}

function StatCard({ icon, label, value, danger }: { icon: React.ReactNode; label: string; value: string | number; danger?: boolean }) {
  return (
    <Col xs={12} md={6}>
      <div className="msp-card" style={{ padding: '16px 18px' }}>
        <Statistic
          title={
            <span style={{ color: danger ? '#cf1322' : '#6b7280', fontSize: 13 }}>
              {icon} {label}
            </span>
          }
          value={value}
          valueStyle={{ color: danger ? '#cf1322' : '#1f2329' }}
        />
      </div>
    </Col>
  );
}

function Entry({ label, desc, disabled, onClick }: { label: string; desc: string; disabled?: boolean; onClick: () => void }) {
  return (
    <Col xs={24} md={8}>
      <Card hoverable={!disabled} style={{ borderRadius: 10, opacity: disabled ? 0.5 : 1 }} onClick={disabled ? undefined : onClick}>
        <div style={{ fontWeight: 600 }}>{label}</div>
        <div style={{ color: '#9aa0a6', fontSize: 13, marginTop: 4 }}>{desc}</div>
      </Card>
    </Col>
  );
}
