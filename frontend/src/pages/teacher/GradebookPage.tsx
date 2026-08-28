import { useEffect, useState } from 'react';
import { App as AntApp, Button, Card, Space, Table, Typography } from 'antd';
import type { TableColumnsType, TableProps } from 'antd';
import { DownloadOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { client, extractApiMessage } from '../../api/client';
import type { ApiResponse } from '../../api/types';
import type { GradebookStudent, GradebookView } from '../../api/gradebookTypes';

/** 纯渲染取值：cells 中匹配 (studentId, assignmentProblemId) 的 bestScore，未提交返回 null（渲染为 —）。 */
function bestScoreOf(
  view: GradebookView,
  studentId: number,
  assignmentProblemId: number,
): number | null {
  return (
    view.cells.find(
      (c) => c.studentId === studentId && c.assignmentProblemId === assignmentProblemId,
    )?.bestScore ?? null
  );
}

/** 纯渲染取值：totals 中该生总分，无条目按 0。 */
function totalOf(view: GradebookView, studentId: number): number {
  return view.totals.find((t) => t.studentId === studentId)?.total ?? 0;
}

/** 解析 JSON 错误体 Blob 里的业务 message；非 JSON 或无 message 走兜底文案。 */
async function messageFromJsonBlob(blob: Blob, fallback: string): Promise<string> {
  try {
    const parsed = JSON.parse(await blob.text()) as { message?: unknown };
    if (typeof parsed.message === 'string' && parsed.message) return parsed.message;
  } catch {
    // 非 JSON 错误体，走兜底
  }
  return fallback;
}

/** axios 错误体若是后端 JSON Blob 则取业务 message，否则走 axios/兜底文案，不静默。 */
async function apiMessageFromBlob(err: unknown, fallback: string): Promise<string> {
  const data = (err as { response?: { data?: unknown } })?.response?.data;
  if (data instanceof Blob && data.type.includes('json')) {
    return messageFromJsonBlob(data, fallback);
  }
  return extractApiMessage(err, fallback);
}

/** 成绩册页：学生×题目得分矩阵（未提交显 —，0 显示 0）+ CSV 导出。 */
export default function GradebookPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>();
  const { message } = AntApp.useApp();
  const [exporting, setExporting] = useState(false);

  const gradebookQuery = useQuery({
    queryKey: ['gradebook', assignmentId],
    queryFn: () =>
      client
        .get<ApiResponse<GradebookView>>(`/assignments/${assignmentId}/gradebook`)
        .then((r) => r.data.data),
    enabled: !!assignmentId,
  });
  const view = gradebookQuery.data;

  useEffect(() => {
    if (gradebookQuery.isError) {
      message.error(extractApiMessage(gradebookQuery.error, '成绩册加载失败'));
    }
  }, [gradebookQuery.isError, gradebookQuery.error, message]);

  // CSV 走 client blob 下载：携带 Authorization 头（window.open 带不了 token，必 401）。
  async function onExportCsv() {
    if (!assignmentId) return;
    setExporting(true);
    try {
      const resp = await client.get(`/assignments/${assignmentId}/gradebook/csv`, {
        responseType: 'blob',
      });
      // 业务错误是 HTTP200+JSON 信封（BizException 一律 200），responseType blob 时
      // 拦截器拿不到 code 放行——错误 JSON 会伪装成 CSV，须在此识别拦截，不触发下载。
      const body = resp.data as unknown;
      if (body instanceof Blob && body.type.includes('json')) {
        message.error(await messageFromJsonBlob(body, '成绩册导出失败，请重试'));
        return;
      }
      const url = URL.createObjectURL(body as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `gradebook-${assignmentId}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      message.success('成绩册 CSV 已导出');
    } catch (err) {
      message.error(await apiMessageFromBlob(err, '成绩册导出失败，请重试'));
    } finally {
      setExporting(false);
    }
  }

  const problemColumns: TableColumnsType<GradebookStudent> = (view?.problems ?? []).map((p) => ({
    title: `${p.title}(${p.score})`,
    key: `ap-${p.assignmentProblemId}`,
    render: (_, student) => {
      const best = view ? bestScoreOf(view, student.studentId, p.assignmentProblemId) : null;
      // 未提交显示 —（数学上计 0）；有提交显示 bestScore，0 也要显示 0
      return best === null ? '—' : best;
    },
  }));

  const columns: TableProps<GradebookStudent>['columns'] = [
    { title: '学号', dataIndex: 'studentNo', key: 'studentNo', width: 130 },
    { title: '姓名', dataIndex: 'realName', key: 'realName', width: 110 },
    ...problemColumns,
    {
      title: '总分',
      key: 'total',
      width: 90,
      render: (_, student) => (view ? totalOf(view, student.studentId) : 0),
    },
  ];

  return (
    <Card
      title={
        <Space>
          <Link to={`/teacher/assignments/${assignmentId}`}>
            <Button size="small" type="text" icon={<ArrowLeftOutlined />}>
              返回作业详情
            </Button>
          </Link>
          <span>成绩册{view ? ` · ${view.assignmentTitle}` : ''}</span>
        </Space>
      }
      extra={
        <Button
          type="primary"
          icon={<DownloadOutlined />}
          loading={exporting}
          onClick={onExportCsv}
        >
          导出 CSV
        </Button>
      }
    >
      <Table<GradebookStudent>
        rowKey="studentId"
        columns={columns}
        dataSource={view?.students ?? []}
        loading={gradebookQuery.isPending}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
      <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
        「—」表示未提交（按 0 分计入）；单元格为该题最高得分。
      </Typography.Paragraph>
    </Card>
  );
}
