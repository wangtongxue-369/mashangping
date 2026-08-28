/**
 * 成绩册/提交历史簇后端出参类型（Task 1 三补端点）。
 * 字段逐个对齐 server 记录（GradebookView / TeacherSubmissionRow），与计划期片段不一致时以 Java 为准：
 * - GET /api/assignments/{id}/gradebook            → GradebookView
 * - GET /api/assignments/{id}/submissions          → MP Page<TeacherSubmissionRow>（信封复用 api/types 的 Page<T>）
 * - GET /api/assignments/{id}/submissions/{sid}    → TeacherSubmissionRow.Detail
 * - GET /api/assignments/{id}/gradebook/csv        → text/csv 字节流附件（无 JSON 信封，须 blob 下载）
 */

/** 成绩册学生行（GradebookView.StudentRow）。 */
export interface GradebookStudent {
  studentId: number;
  studentNo: string;
  realName: string;
}

/** 成绩册题目列（GradebookView.ProblemRow，按 sortOrder 升序给出）。 */
export interface GradebookProblem {
  assignmentProblemId: number;
  problemId: number;
  title: string;
  score: number;
  sortOrder: number;
}

/** 成绩册格子（GradebookView.Cell）：该生该题最高得分；未提交无对应条目。 */
export interface GradebookCell {
  studentId: number;
  assignmentProblemId: number;
  bestScore: number;
}

/** 成绩册总分（GradebookView.Total）：仅"有提交"的学生有条目。 */
export interface GradebookTotal {
  studentId: number;
  total: number;
}

/** 成绩册矩阵视图（GradebookView）：纯渲染数据源，聚合已由后端完成。 */
export interface GradebookView {
  assignmentId: number;
  assignmentTitle: string;
  students: GradebookStudent[];
  problems: GradebookProblem[];
  cells: GradebookCell[];
  totals: GradebookTotal[];
}

/** 教师提交历史行（TeacherSubmissionRow record）。学号/姓名/题名在账号或题目缺失时为 null。 */
export interface TeacherSubmissionRow {
  submissionId: number;
  studentNo: string | null;
  studentName: string | null;
  problemTitle: string | null;
  language: string;
  status: string;
  score: number | null;
  passedCount: number | null;
  totalCount: number | null;
  timeUsedMs: number | null;
  memoryUsedMb: number | null;
  /** 后端 LocalDateTime 序列化字符串（yyyy-MM-ddTHH:mm:ss）。 */
  submittedAt: string;
  isLate: boolean;
}

/** 判题样例点（TeacherSubmissionRow.SamplePoint）：七字段全量；message 仅为失败信息（如 RE stderr）。 */
export interface SamplePoint {
  pointIndex: number;
  status: string;
  timeUsedMs: number | null;
  memoryUsedMb: number | null;
  input: string | null;
  expectedOutput: string | null;
  message: string | null;
}

/** 隐藏点瘦身视图（TeacherSubmissionRow.MaskedPoint）：输入/期望输出/信息零泄漏。 */
export interface MaskedPoint {
  pointIndex: number;
  status: string;
  timeUsedMs: number | null;
  memoryUsedMb: number | null;
}

/** 教师提交详情（TeacherSubmissionRow.Detail）：代码全文 + 样例点全量 + 隐藏点瘦身。 */
export interface TeacherSubmissionDetail {
  submissionId: number;
  code: string;
  language: string;
  status: string;
  samples: SamplePoint[];
  maskedPoints: MaskedPoint[];
}
