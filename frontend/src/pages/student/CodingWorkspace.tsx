import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Editor from '@monaco-editor/react';
import { App as AntApp, Button, Descriptions, Drawer, Empty, Select, Spin, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { client } from '../../api/client';
import { useAuth } from '../../auth/useAuth';
import { connect } from '../../api/ws';
import type { MaskedPoint, SamplePoint, SubmissionDetail, SubmissionSummary } from '../../api/studentTypes';

/** 学生提交历史锚（作业/练习互斥）。 */
export type HistoryAnchor = { assignmentProblemId: number } | { problemId: number };

/** 提交目标（作业题用 assignmentProblemId、练习用 problemId）。 */
export type SubmitTarget =
  | { type: 'assignment'; assignmentProblemId: number }
  | { type: 'practice'; problemId: number };

export interface CodingWorkspaceProps {
  languages: string[];
  submitTarget: SubmitTarget;
  historyAnchor: HistoryAnchor;
}

/** 语言键 → 展示名 / Monaco 语言。 */
const LANGUAGE_META: Record<string, { label: string; monaco: string }> = {
  C: { label: 'C', monaco: 'c' },
  CPP: { label: 'C++', monaco: 'cpp' },
  JAVA: { label: 'Java', monaco: 'java' },
  PYTHON: { label: 'Python', monaco: 'python' },
};

const STARTER: Record<string, string> = {
  C: '#include <stdio.h>\n\nint main(void) {\n    return 0;\n}\n',
  CPP: '#include <iostream>\n\nint main() {\n    return 0;\n}\n',
  JAVA: 'public class Main {\n    public static void main(String[] args) {\n    }\n}\n',
  PYTHON: '# -*- coding: utf-8 -*-\n',
};

/** 提交状态 → 徽标文案与颜色。 */
const SUB_STATUS: Record<string, { label: string; color: string }> = {
  PENDING: { label: '排队中', color: 'gold' },
  RUNNING: { label: '评测中', color: 'blue' },
  AC: { label: '通过', color: 'green' },
  WA: { label: '答案错误', color: 'red' },
  TLE: { label: '超时', color: 'orange' },
  MLE: { label: '内存超限', color: 'purple' },
  RE: { label: '运行时错误', color: 'volcano' },
  CE: { label: '编译错误', color: 'cyan' },
  SYSTEM_ERROR: { label: '系统错误', color: 'black' },
};

function subStatusTag(s: string) {
  const m = SUB_STATUS[s] ?? { label: s, color: 'default' };
  return <Tag color={m.color}>{m.label}</Tag>;
}

/**
 * 编码工作区（作业/练习共用）：语言选择 + Monaco 编辑 + 提交 + 我的提交历史 +
 * 详情抽屉（样例点完整输入输出，隐藏点零泄漏）+ 判题进度实时推送。
 */
export default function CodingWorkspace({ languages, submitTarget, historyAnchor }: CodingWorkspaceProps) {
  const { token } = useAuth();
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const [language, setLanguage] = useState(languages[0] ?? 'C');
  const [code, setCode] = useState(() => STARTER[languages[0] ?? 'C'] ?? '');
  const [detailId, setDetailId] = useState<number | null>(null);
  const latestIdRef = useRef<number | null>(null);

  const anchorKey = useMemo(() => JSON.stringify(historyAnchor), [historyAnchor]);
  const historyQueryKey = ['mySubmissions', historyAnchor];

  const historyParams = useMemo(() => {
    if ('assignmentProblemId' in historyAnchor) {
      return { assignmentProblemId: historyAnchor.assignmentProblemId };
    }
    return { problemId: historyAnchor.problemId };
  }, [historyAnchor]);

  const { data: history, isLoading: historyLoading } = useQuery<SubmissionSummary[]>({
    queryKey: historyQueryKey,
    queryFn: async () => {
      const resp = await client.get('/submissions/my', { params: historyParams });
      return resp.data.data;
    },
  });

  const { data: detail, isLoading: detailLoading } = useQuery<SubmissionDetail>({
    queryKey: ['submissionDetail', detailId],
    queryFn: async () => {
      const resp = await client.get(`/submissions/${detailId}`);
      return resp.data.data;
    },
    enabled: detailId != null,
  });

  const submit = useMutation({
    mutationFn: async (payload: { language: string; code: string }) => {
      const body =
        submitTarget.type === 'assignment'
          ? { assignmentProblemId: submitTarget.assignmentProblemId, language: payload.language, code: payload.code }
          : { problemId: submitTarget.problemId, language: payload.language, code: payload.code };
      const resp = await client.post('/submissions', body);
      return resp.data.data;
    },
    onSuccess: (created: { id: number; status: string }) => {
      latestIdRef.current = created.id;
      message.success('提交成功，开始评测');
      queryClient.invalidateQueries({ queryKey: historyQueryKey });
    },
    onError: (err: unknown) => {
      message.error((err as { message?: string })?.message ?? '提交失败，请重试');
    },
  });

  // 判题进度实时推送：best-effort，收到匹配 FINISHED 后刷新我的提交历史；卸载断开。
  useEffect(() => {
    if (!token) return undefined;
    return connect({
      token,
      onEvent: (e) => {
        if (e.type === 'FINISHED' && latestIdRef.current != null && e.submissionId === latestIdRef.current) {
          queryClient.invalidateQueries({ queryKey: historyQueryKey });
        }
      },
    });
  }, [token, anchorKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const onLanguageChange = (next: string) => {
    setLanguage(next);
    if (!code) {
      setCode(STARTER[next] ?? '');
    }
  };

  const columns: ColumnsType<SubmissionSummary> = [
    { title: '提交', dataIndex: 'id', width: 80 },
    { title: '状态', dataIndex: 'status', render: (s: string) => subStatusTag(s) },
    { title: '得分', dataIndex: 'score', width: 80, render: (v: number | null) => (v == null ? '—' : String(v)) },
    { title: '语言', dataIndex: 'language', width: 90 },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      render: (v: string) => (v ? v.replace('T', ' ').slice(0, 19) : '-'),
    },
  ];

  const sampleDesc = (p: SamplePoint) => (
    <div key={p.pointIndex} style={{ marginBottom: 12 }}>
      <div>
        样例点 {p.pointIndex + 1}：{subStatusTag(p.status)} {p.timeUsedMs ?? '-'}ms / {p.memoryUsedMb ?? '-'}MB
      </div>
      {p.message ? <div style={{ color: '#c00' }}>{p.message}</div> : null}
      <div style={{ marginTop: 4 }}>
        <b>输入</b>
        <pre style={{ background: '#f5f5f5', padding: 8 }}>{p.input}</pre>
        <b>预期输出</b>
        <pre style={{ background: '#f5f5f5', padding: 8 }}>{p.expectedOutput}</pre>
      </div>
    </div>
  );

  const maskedDesc = (p: MaskedPoint) => (
    <div key={p.pointIndex}>
      第 {p.pointIndex + 1} 个测试点：{subStatusTag(p.status)} {p.timeUsedMs ?? '-'}ms / {p.memoryUsedMb ?? '-'}MB
    </div>
  );

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Select
          value={language}
          style={{ width: 140 }}
          onChange={onLanguageChange}
          options={languages.map((l) => ({ value: l, label: LANGUAGE_META[l]?.label ?? l }))}
        />
        <Button type="primary" style={{ marginLeft: 12 }} loading={submit.isPending} onClick={() => submit.mutate({ language, code })}>
          提交评测
        </Button>
        {submitTarget.type === 'practice' ? <Tag style={{ marginLeft: 12 }}>自由练习</Tag> : null}
      </div>

      <Editor
        height="360px"
        language={LANGUAGE_META[language]?.monaco ?? 'plaintext'}
        value={code}
        onChange={(v) => setCode(v ?? '')}
      />

      <h3>我的提交</h3>
      <Table<SubmissionSummary>
        rowKey="id"
        size="small"
        loading={historyLoading}
        columns={columns}
        dataSource={history ?? []}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        onRow={(row) => ({ onClick: () => setDetailId(row.id), style: { cursor: 'pointer' } })}
      />

      <Drawer
        title={detail ? `提交 #${detail.id} · ${subStatusTag(detail.status)}` : '提交详情'}
        width={Math.min(window.innerWidth || 800, 720)}
        open={detailId != null}
        onClose={() => setDetailId(null)}
      >
        {detailLoading ? (
          <Spin />
        ) : detail ? (
          <div>
            <Editor
              height="200px"
              language={LANGUAGE_META[detail.language]?.monaco ?? 'plaintext'}
              value={detail.code}
              options={{ readOnly: true }}
            />
            <Descriptions
              column={2}
              size="small"
              style={{ margin: '16px 0' }}
              items={[
                { key: 'status', label: '状态', children: subStatusTag(detail.status) },
                { key: 'score', label: '得分', children: detail.score == null ? '—' : String(detail.score) },
                { key: 'passed', label: '通过', children: `${detail.passedCount ?? 0} / ${detail.totalCount ?? 0}` },
                { key: 'time', label: '用时', children: detail.timeUsedMs == null ? '—' : `${detail.timeUsedMs}ms` },
                { key: 'mem', label: '内存', children: detail.memoryUsedMb == null ? '—' : `${detail.memoryUsedMb}MB` },
                { key: 'late', label: '迟交', children: detail.isLate ? '是' : '否' },
                { key: 'lang', label: '语言', children: detail.language },
                { key: 'at', label: '提交时间', children: detail.submittedAt.replace('T', ' ').slice(0, 19) },
              ]}
            />
            <h4>测试点结果</h4>
            {detail.samples.map((p) => sampleDesc(p))}
            <h4>隐藏测试点（未通过不显示输入输出）</h4>
            {detail.maskedPoints.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无隐藏测试点信息" />
            ) : (
              detail.maskedPoints.map((p) => maskedDesc(p))
            )}
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}
