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
