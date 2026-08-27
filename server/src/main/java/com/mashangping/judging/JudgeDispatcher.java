package com.mashangping.judging;

/** 判题启动钩子：提交事务提交后异步驱动一轮领取。实现者=T5 JudgeScheduler */
public interface JudgeDispatcher {

    /** 不等待下一个轮询 tick；允许与定时器重入竞争（实现内部护栏去重） */
    void dispatchAsync();
}
