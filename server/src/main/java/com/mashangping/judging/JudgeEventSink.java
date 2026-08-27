package com.mashangping.judging;

/** 判题过程中的事件出口：推送与收集共用此通道 */
public interface JudgeEventSink {

    /** 开判通知（编译即将开始） */
    void judging();

    /** 每个测试点结束即发 */
    void point(PointOutcome outcome);

    /** 编译失败终局通知 */
    void compileError(String message);
}
