/**
 * 教师端共享展示常量（避免各页重复定义造成漂移）。
 * 作业/提交状态徽标直接复用学生端已收敛口径（pages/student/constants.ts 同源同色），
 * 保证同角色内与跨角色（学生端列表/教师端列表）文案颜色一致。
 */
import { ASSIGN_STATUS, SUBMIT_STATUS, statusMeta } from '../student/constants';

export const SUBMIT_STATUS_META = SUBMIT_STATUS;
export const ASSIGN_STATUS_META = ASSIGN_STATUS;
export { statusMeta };
