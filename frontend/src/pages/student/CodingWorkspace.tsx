import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Editor from '@monaco-editor/react';
import {
  App as AntApp,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { client } from '../../api/client';
import { useAuth } from '../../auth/useAuth';
import { connect } from '../../api/ws';
import MarkdownView from '../../components/MarkdownView';
import type { MaskedPoint, SamplePoint, StudentSample, SubmissionDetail, SubmissionSummary } from '../../api/studentTypes';
import { LANGUAGE_LABEL, LANGUAGE_MONACO, SUBMIT_STATUS, fmtDateTime, statusMeta } from './constants';

/** 学生提交历史锚（作业/练习互斥）。 */
export type HistoryAnchor = { assignmentProblemId: number } | { problemId: number };

/** 提交目标（作业题用 assignmentProblemId、练习用 problemId）。 */
export type SubmitTarget =
  | { type: 'assignment'; assignmentProblemId: number }
  | { type: 'practice'; problemId: number };

export interface CodingWorkspaceProps {
  title: string;
  description: string;
  samples: StudentSample[];
  languages: string[];
  timeLimitMs: number;
  memoryLimitMb: number;
  submitTarget: SubmitTarget;
  historyAnchor: HistoryAnchor;
  typeTag?: string;
}

const STARTER: Record<string, string> = {
  C: '#include <stdio.h>\n\nint main(void) {\n    return 0;\n}\n',
  CPP: '#include <iostream>\n\nint main() {\n    return 0;\n}\n',
  JAVA: 'public class Main {\n    public static void main(String[] args) {\n    }\n}\n',
  PYTHON: '# -*- coding: utf-8 -*-\n',
};

/**
 * 牛客式双栏答题工作区（作业/练习共用）：
 * - 左栏「题目描述 / 提交记录」Tab：Markdown 题面 + 样例点输入输出；提交记录表格（点行看详情抽屉）
 * - 右栏编码区：语言选择 + 提交按钮 + Monaco（深色主题） + 实时判题进度条
 * - 详情抽屉：样例点完整输入输出；隐藏点仅状态/用时/内存（零泄漏红线）
 */
export default function CodingWorkspace({
  title,
  description,
  samples,
  languages,
  timeLimitMs,
  memoryLimitMb,
  submitTarget,
  historyAnchor,
  typeTag,
}: CodingWorkspaceProps) {
  const { token } = useAuth();
  const { message } = AntApp.useApp();
  const queryClient = useQueryClient();

  const [language, setLanguage] = useState(languages[0] ?? 'C');
  const [code, setCode] = useState(() => STARTER[languages[0] ?? 'C'] ?? '');
  const [detailId, setDetailId] = useState<number | null>(null);
  const [live, setLive] = useState<string | null>(null);
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

  /** 我的最高分：全部提交中 score 非空的最大值；无判分结果为 null。 */
  const bestScore = useMemo(() => {
    const scores = (history ?? []).map((h) => h.score).filter((s): s is number => s != null);
    return scores.length === 0 ? null : Math.max(...scores);
  }, [history]);

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
      setLive('已提交，等待评测…');
      message.success('提交成功，开始评测');
      queryClient.invalidateQueries({ queryKey: historyQueryKey });
    },
    onError: (err: unknown) => {
      message.error((err as { message?: string })?.message ?? '提交失败，请重试');
    },
  });

  // 判题进度实时推送：best-effort，只消费「最近一次提交」的事件；FINISHED 后刷新历史。
  useEffect(() => {
    if (!token) return undefined;
    return connect({
      token,
      onEvent: (e) => {
        if (e.submissionId !== latestIdRef.current) return;
        switch (e.type) {
          case 'JUDGING':
            setLive('评测中…');
            break;
          case 'POINT':
            setLive(`第 ${(e.pointIndex ?? 0) + 1} 个测试点：${statusMeta(SUBMIT_STATUS, e.status ?? '').label}`);
            break;
          case 'COMPILE_ERROR':
            setLive(`编译错误：${e.message ?? '（无输出）'}`);
            break;
          case 'FINISHED':
            setLive(`评测完成：${statusMeta(SUBMIT_STATUS, e.finalStatus ?? '').label}`);
            queryClient.invalidateQueries({ queryKey: historyQueryKey });
            break;
          default:
            break;
        }
      },
    });
  }, [token, anchorKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const refreshHistory = () => {
    queryClient.invalidateQueries({ queryKey: historyQueryKey });
  };

  const onLanguageChange = (next: string) => {
    setLanguage(next);
    if (!code) {
      setCode(STARTER[next] ?? '');
    }
  };

  const statusTag = (s: string) => {
    const m = statusMeta(SUBMIT_STATUS, s);
    return <Tag color={m.color}>{m.label}</Tag>;
  };

  const historyColumns: ColumnsType<SubmissionSummary> = [
    { title: '提交', dataIndex: 'id', width: 64 },
    { title: '状态', dataIndex: 'status', render: (s: string) => statusTag(s) },
    { title: '得分', dataIndex: 'score', width: 64, render: (v: number | null) => (v == null ? '—' : String(v)) },
    { title: '时间', dataIndex: 'submittedAt', render: (v: string) => fmtDateTime(v) },
  ];

  const statementPane = (
    <div className="solve-statement">
      <h2 style={{ margin: '0 0 8px', fontSize: 18 }}>{title}</h2>
      <Space size={8} wrap style={{ marginBottom: 10 }}>
        <Tag color="blue" style={{ marginInlineEnd: 0 }}>
          {typeTag ?? (submitTarget.type === 'assignment' ? '作业题' : '自由练习')}
        </Tag>
        <span style={{ color: '#6b7280', fontSize: 13 }}>
          时间限制 {timeLimitMs}ms · 内存限制 {memoryLimitMb}MB
        </span>
        <Tag style={{ marginInlineEnd: 0 }} color={bestScore == null ? 'default' : 'green'}>
          我的最高分：{bestScore == null ? '—' : bestScore}
        </Tag>
      </Space>
      <MarkdownView source={description} />
      {samples.length > 0 ? (
        <>
          <h3 style={{ margin: '14px 0 4px' }}>示例</h3>
          {samples.map((s, i) => (
            <div key={i} className="solve-sample">
              <b>样例 {i + 1}</b>
              <div style={{ color: '#6b7280', fontSize: 13, marginTop: 4 }}>输入</div>
              <pre>{s.input}</pre>
              <div style={{ color: '#6b7280', fontSize: 13 }}>输出</div>
              <pre>{s.output}</pre>
            </div>
          ))}
        </>
      ) : null}
    </div>
  );

  const historyPane = (
    <div style={{ maxHeight: 'calc(100vh - 260px)', overflowY: 'auto' }}>
      <Table<SubmissionSummary>
        rowKey="id"
        size="small"
        loading={historyLoading}
        columns={historyColumns}
        dataSource={history ?? []}
        pagination={{ pageSize: 8, showSizeChanger: false }}
        onRow={(row) => ({ onClick: () => setDetailId(row.id), style: { cursor: 'pointer' } })}
        locale={{ emptyText: '还没有提交记录，快在右侧写第一段代码吧' }}
      />
    </div>
  );

  const sampleDesc = (p: SamplePoint) => (
    <div key={p.pointIndex} style={{ marginBottom: 12 }}>
      <div>
        样例点 {p.pointIndex + 1}：{statusTag(p.status)} {p.timeUsedMs ?? '-'}ms / {p.memoryUsedMb ?? '-'}MB
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
      第 {p.pointIndex + 1} 个测试点：{statusTag(p.status)} {p.timeUsedMs ?? '-'}ms / {p.memoryUsedMb ?? '-'}MB
    </div>
  );

  return (
    <div className="solve-grid">
      {/* 左栏：题面 + 提交记录 */}
      <div className="msp-card" style={{ padding: '16px 20px' }}>
        <Tabs
          defaultActiveKey="desc"
          items={[
            { key: 'desc', label: '题目描述', children: statementPane },
            {
              key: 'subs',
              label: (
                <Space size={4}>
                  提交记录
                  {history && history.length > 0 ? (
                    <span style={{ color: '#9aa0a6', fontSize: 12 }}>{history.length}</span>
                  ) : null}
                </Space>
              ),
              children: historyPane,
            },
          ]}
        />
      </div>

      {/* 右栏：编码区 */}
      <div className="msp-card" style={{ padding: '12px 16px', position: 'sticky', top: 72 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
          <Select
            value={language}
            style={{ width: 132 }}
            onChange={onLanguageChange}
            options={languages.map((l) => ({ value: l, label: LANGUAGE_LABEL[l] ?? l }))}
          />
          <Button icon={<ReloadOutlined />} onClick={refreshHistory} title="刷新提交记录" />
          <div style={{ flex: 1 }} />
          <Button type="primary" loading={submit.isPending} onClick={() => submit.mutate({ language, code })}>
            提交评测
          </Button>
        </div>
        <div style={{ height: 'calc(100vh - 330px)', minHeight: 420, border: '1px solid #1e1e1e', borderRadius: 8, overflow: 'hidden' }}>
          <Editor
            height="100%"
            theme="vs-dark"
            language={LANGUAGE_MONACO[language] ?? 'plaintext'}
            value={code}
            onChange={(v) => setCode(v ?? '')}
            options={{ fontSize: 14, minimap: { enabled: false }, scrollBeyondLastLine: false }}
          />
        </div>
        <div className="solve-live" style={{ marginTop: 10 }}>
          {live ? (
            <>
              <Tag color="geekblue" style={{ marginInlineEnd: 0 }}>
                评测进度
              </Tag>
              <span>{live}</span>
            </>
          ) : (
            <span style={{ color: '#9aa0a6' }}>编写代码后点击「提交评测」，判题进度会实时显示在这里。</span>
          )}
        </div>
      </div>

      {/* 详情抽屉：样例点完整 / 隐藏点零泄漏 */}
      <Drawer
        title={detail ? `提交 #${detail.id} · ${statusTag(detail.status)}` : '提交详情'}
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
              theme="vs-dark"
              language={LANGUAGE_MONACO[detail.language] ?? 'plaintext'}
              value={detail.code}
              options={{ readOnly: true }}
            />
            <Descriptions
              column={2}
              size="small"
              style={{ margin: '16px 0' }}
              items={[
                { key: 'status', label: '状态', children: statusTag(detail.status) },
                { key: 'score', label: '得分', children: detail.score == null ? '—' : String(detail.score) },
                { key: 'passed', label: '通过', children: `${detail.passedCount ?? 0} / ${detail.totalCount ?? 0}` },
                { key: 'time', label: '用时', children: detail.timeUsedMs == null ? '—' : `${detail.timeUsedMs}ms` },
                { key: 'mem', label: '内存', children: detail.memoryUsedMb == null ? '—' : `${detail.memoryUsedMb}MB` },
                { key: 'late', label: '迟交', children: detail.isLate ? '是' : '否' },
                { key: 'lang', label: '语言', children: detail.language },
                { key: 'at', label: '提交时间', children: fmtDateTime(detail.submittedAt) },
              ]}
            />
            <h4>测试点结果</h4>
            {detail.samples.map((p) => sampleDesc(p))}
            <h4>隐藏测试点（仅展示状态/用时/内存，不展示输入输出）</h4>
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
