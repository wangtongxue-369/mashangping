/**
 * 学生侧出参 TS 类型（对齐后端 record，字段名以后端 server 代码为准）：
 * - MyCourseView: EnrollmentService 出参 record
 * - StudentListItem / StudentProblemItem / StudentProblemDetail / StudentSample: AssignmentViews 学生分型
 * - AssignmentStatus: AssignmentStatus 枚举
 * - Summary / SamplePoint / MaskedPoint / Detail: StudentSubmissionViews 学生分型
 * - JudgeProgressEvent: ws.JudgeProgressEvent 推送载荷
 */

import type { Page } from './types';

/** 学生作业状态（AssignmentStatus 枚举，实时推算，不落库）。 */
export type AssignmentStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'LATE_WINDOW' | 'CLOSED';

/** 我的选修课程列表行（GET /api/courses/my，courseId 为主键）。 */
export interface MyCourseView {
  courseId: number;
  name: string;
  term: string;
}

/** 课程作业分页（GET /api/courses/{courseId}/assignments）。 */
export type StudentAssignmentPage = Page<StudentListItem>;

/**
 * 课程作业列表行（AssignmentViews.StudentListItem）。status 展示为状态徽标；
 * NOT_STARTED 时题目不可进入（前端引导，后端 requireStarted 兜底）。
 */
export interface StudentListItem {
  id: number;
  title: string;
  startAt: string;
  dueAt: string;
  lateDays: number;
  problemCount: number;
  status: AssignmentStatus;
}

/**
 * 学生题目列表行（AssignmentViews.StudentProblemItem），补返 assignmentProblemId
 * 支撑作业题提交锚（由另一子代理同时补齐后端字段，前端按含该字段声明）。
 */
export interface StudentProblemItem {
  assignmentProblemId: number;
  problemId: number;
  title: string;
  score: number;
  sortOrder: number;
  languages: string[];
  timeLimitMs: number;
  memoryLimitMb: number;
}

/** 样例点（学生阅读视图可见完整输入输出）。 */
export interface StudentSample {
  input: string;
  output: string;
}

/**
 * 学生作业上下文头（AssignmentViews.StudentAssignmentHeader，GET /api/assignments/{id} 学生分支）：
 * 深链进入题目/编码页时提供课程名/作业名/状态/时间窗，作业题目数与状态在列表页复用。
 */
export interface StudentAssignmentHeader {
  courseId: number;
  courseName: string;
  assignmentId: number;
  title: string;
  status: AssignmentStatus;
  startAt: string;
  dueAt: string;
  lateDays: number;
  problemCount: number;
}

/** 学生题目阅读视图（AssignmentViews.StudentProblemDetail）：MD 题面 + 题分(满分) + 样例集合。 */
export interface StudentProblemDetail {
  assignmentProblemId: number;
  problemId: number;
  title: string;
  description: string;
  languages: string[];
  timeLimitMs: number;
  memoryLimitMb: number;
  /** 该题满分（作业题由 AssignmentProblem.score 给出），用于「最高分/满分」语义展示。 */
  score: number;
  samples: StudentSample[];
}

/**
 * 学生提交列表项（StudentSubmissionViews.Summary）。
 * 未判分(PENDING)时后端出参不含 score 键 → score 为 null。
 */
export interface SubmissionSummary {
  id: number;
  status: string;
  score: number | null;
  isLate: boolean;
  language: string;
  submittedAt: string;
}

/** 样例点详情（完整输入输出，规格仅样例点下发；actualOutput=WA 时后端写回的「你的输出」）。 */
export interface SamplePoint {
  pointIndex: number;
  status: string;
  timeUsedMs: number | null;
  memoryUsedMb: number | null;
  input: string;
  expectedOutput: string;
  message: string;
  actualOutput?: string | null;
}

/** 隐藏点瘦视图（零泄漏：除四字段外不携带任何内容）。 */
export interface MaskedPoint {
  pointIndex: number;
  status: string;
  timeUsedMs: number | null;
  memoryUsedMb: number | null;
}

/** 学生提交详情（StudentSubmissionViews.Detail）：样例点完整 / 隐藏点仅瘦视图。 */
export interface SubmissionDetail {
  id: number;
  problemId: number;
  language: string;
  code: string;
  status: string;
  score: number | null;
  passedCount: number | null;
  totalCount: number | null;
  timeUsedMs: number | null;
  memoryUsedMb: number | null;
  isLate: boolean;
  submittedAt: string;
  samples: SamplePoint[];
  maskedPoints: MaskedPoint[];
}

/**
 * 判题进度推送载荷（ws.JudgeProgressEvent）：固定字段全集，按 type 只填相关键位。
 * type ∈ JUDGING | POINT | COMPILE_ERROR | FINISHED。
 */
export interface JudgeProgressEvent {
  type: string;
  submissionId: number | null;
  pointIndex: number | null;
  status: string | null;
  message: string | null;
  finalStatus: string | null;
  score: number | null;
  passedCount: number | null;
  totalCount: number | null;
}

/** 公开题库摘要（PracticeViews.Summary）：自由练习列表行。 */
export interface PracticeSummary {
  id: number;
  title: string;
  languages: string[];
  timeLimitMs: number;
  memoryLimitMb: number;
}

/** 公开题详情（PracticeViews.Detail）：MD 题面 + 样例点完整输入输出（练习无隐藏点概念）。 */
export interface PracticeDetail {
  id: number;
  title: string;
  description: string;
  languages: string[];
  timeLimitMs: number;
  memoryLimitMb: number;
  samples: StudentSample[];
}
