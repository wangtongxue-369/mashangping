/** 学生端共享展示常量与格式化工具（避免语言键/状态映射在多页重复定义造成漂移）。 */

/** 语言键 → 展示名（键与后端 Languages.ALL 一致）。 */
export const LANGUAGE_LABEL: Record<string, string> = {
  C: 'C',
  CPP: 'C++',
  JAVA: 'Java',
  PYTHON: 'Python',
};

/** 语言键 → Monaco language。 */
export const LANGUAGE_MONACO: Record<string, string> = {
  C: 'c',
  CPP: 'cpp',
  JAVA: 'java',
  PYTHON: 'python',
};

export interface StatusMeta {
  label: string;
  color: string;
}

/** 提交状态 → 徽标文案与颜色。 */
export const SUBMIT_STATUS: Record<string, StatusMeta> = {
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

/** 作业状态 → 徽标文案与颜色。 */
export const ASSIGN_STATUS: Record<string, StatusMeta> = {
  NOT_STARTED: { label: '未开始', color: 'default' },
  IN_PROGRESS: { label: '进行中', color: 'blue' },
  LATE_WINDOW: { label: '宽限中', color: 'orange' },
  CLOSED: { label: '已截止', color: 'default' },
};

/** 取状态展示元信息（未知状态原样兜底）。 */
export function statusMeta(map: Record<string, StatusMeta>, key: string): StatusMeta {
  return map[key] ?? { label: key, color: 'default' };
}

/** ISO → 'YYYY-MM-DD HH:mm' */
export function fmtTime(iso?: string | null): string {
  if (!iso) return '-';
  return iso.replace('T', ' ').slice(0, 16);
}

/** ISO → 'YYYY-MM-DD HH:mm:ss' */
export function fmtDateTime(iso?: string | null): string {
  if (!iso) return '-';
  return iso.replace('T', ' ').slice(0, 19);
}
