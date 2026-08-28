/**
 * 判题进度 WebSocket 骨架。
 * 本期教师端不消费实时判题推送（提交历史是查询而非实时），
 * 仅保留类型 + 清理骨架，供后续任务按需接入 /ws 推送。
 */

export interface JudgeProgressEvent {
  submissionId: number;
  progress: number; // 0-100
  status?: string;
  message?: string;
}

/** 本期无订阅实现——教师端为查询型消费，暂不建连。 */
export function subscribeJudgeProgress(): () => void {
  return () => {};
}

export function disconnect(): void {
  // 占位清理骨架，无活动连接时可安全调用。
}