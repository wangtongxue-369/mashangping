/** 教师端代码查重出参类型（对齐后端 PlagiarismViews）。 */

/** 参与比对的提交作者信息（最高分提交 id 供对比取码）。 */
export interface PlagiarismStudentInfo {
  studentId: number;
  studentNo: string;
  realName: string;
  language: string;
  score: number | null;
  ac: boolean;
  submissionId: number;
}

/** 疑似对。 */
export interface PlagiarismPair {
  a: PlagiarismStudentInfo;
  b: PlagiarismStudentInfo;
  similarity: number;
  flag: 'HIGH' | 'MID';
}

/** 查重报告：某作业某题同班两两比对（按相似度降序）。 */
export interface PlagiarismReport {
  assignmentId: number;
  problemId: number;
  problemTitle: string;
  highThreshold: number;
  participants: number;
  items: PlagiarismPair[];
}

/** 对比视图：两名学生最高分提交源码。 */
export interface PlagiarismCompare {
  a: PlagiarismStudentInfo;
  b: PlagiarismStudentInfo;
  codeA: string;
  codeB: string;
}
