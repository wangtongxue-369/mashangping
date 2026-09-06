/**
 * 后端出参 TS 类型（仅本任务消费的最小集，后续任务按需追加）。
 * 字段以后端 server 代码为准：
 * - CourseView: CourseController 出参 record
 * - EnrollmentView: EnrollmentService 出参 record
 * - ImportResult: ExcelImportService.ImportResult（部分成功模式）
 */

/** 后端统一信封 ApiResponse<T>：{ code, message, data }。 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

/** MyBatis Plus Page 序列化子集：前端只消费这四个字段。 */
export interface Page<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

/** 课程出参视图。 */
export interface CourseView {
  id: number;
  name: string;
  term: string;
  description: string | null;
}

/** 课程成员出参：displayName 已激活取账号实名，待激活取导入快照；status ∈ ACTIVE | PENDING。 */
export interface EnrollmentView {
  id: number;
  studentNo: string;
  displayName: string;
  status: string;
}

/** Excel 导入的单行失败明细（row 为 Excel 展示行号，表头计第 1 行）。 */
export interface ImportRowFailure {
  row: number;
  reason: string;
}

/** Excel 导入结果：激活/待激活/重复跳过/失败同行互补。 */
export interface ImportResult {
  totalRows: number;
  activated: number;
  pending: number;
  skipped: number;
  failures: ImportRowFailure[];
}

/**
 * 题目列表出参视图（ProblemController.list 出参 record，不含 MD 正文）。
 * languages 为语言键（C/CPP/JAVA/PYTHON），null 存储值已由后端解析为全支持全集。
 */
export interface ProblemSummaryView {
  id: number;
  title: string;
  languages: string[];
  timeLimitMs: number;
  memoryLimitMb: number;
  isPublic: boolean;
  createdAt: string;
}

/** 测试点出参视图（属主视角，含隐藏点）。 */
export interface TestCaseView {
  id: number;
  input: string;
  expectedOutput: string;
  isSample: boolean;
}

/** 题目详情出参视图（属主视角）：MD 原文 + 全部测试点（含隐藏点）。 */
export interface ProblemDetailView extends Omit<ProblemSummaryView, 'createdAt'> {
  description: string;
  testCases: TestCaseView[];
  createdAt: string;
  updatedAt: string;
}

/**
 * 教师作业列表行（AssignmentViews.TeacherListItem）。
 * 不含 courseId/description——编辑/发布开关前须先拉详情；status 为后端实时推算。
 */
export interface AssignmentTeacherItem {
  id: number;
  title: string;
  startAt: string;
  dueAt: string;
  lateDays: number;
  isPublished: boolean;
  problemCount: number;
  totalScore: number;
  status: string;
}

/** 已选题目行（AssignmentViews.TeacherDetail.ProblemItem，按 sortOrder 升序给出）。 */
export interface AssignmentProblemItem {
  problemId: number;
  title: string;
  score: number;
  sortOrder: number;
}

/** 教师作业详情（AssignmentViews.TeacherDetail）：全字段 + 课程名 + 已选题目清单。 */
export interface AssignmentTeacherDetail {
  id: number;
  courseId: number;
  courseName: string;
  title: string;
  description: string | null;
  startAt: string;
  dueAt: string;
  lateDays: number;
  isPublished: boolean;
  status: string;
  problems: AssignmentProblemItem[];
}

/** 课程选题列表行（CourseProblemView）：addProblems 的合法选题范围。 */
export interface CourseProblemView {
  problemId: number;
  title: string;
  languages: string[];
  timeLimitMs: number;
  memoryLimitMb: number;
  isPublic: boolean;
  testCaseCount: number;
  sortOrder: number;
}
