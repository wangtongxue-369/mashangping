/** 课程工作台聚合出参类型（对齐后端 AnalyticsViews）。 */

export interface CourseOverview {
  courseId: number;
  studentCount: number;
  assignmentCount: number;
  publishedAssignmentCount: number;
  courseProblemCount: number;
  latestAvg: number | null;
  highPlagiarismPairs: number;
}

export interface ScorePoint {
  assignmentId: number;
  title: string;
  startAt: string;
  dueAt: string;
  avg: number;
  max: number;
  min: number;
  submittedCount: number;
}

export interface ScoreDistribution {
  assignmentId: number;
  assignmentTitle: string;
  buckets: { label: string; count: number }[];
}

export interface AcRateRow {
  assignmentId: number;
  assignmentTitle: string;
  problemId: number;
  problemTitle: string;
  acStudents: number;
  submittedStudents: number;
  rate: number;
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface TimelinePoint {
  date: string;
  count: number;
}

export interface PlagiarismRiskRow {
  assignmentId: number;
  assignmentTitle: string;
  problemId: number;
  problemTitle: string;
  highPairs: number;
  midPairs: number;
}
