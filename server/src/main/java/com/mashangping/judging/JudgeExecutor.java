package com.mashangping.judging;

/** 判题引擎 SPI：T7 提供真沙箱实现；测试用替身注入 */
public interface JudgeExecutor {

    /** 引擎可用性探针：不可用时调度器整轮让出（daemon 故障降级语义） */
    default boolean isHealthy() {
        return true;
    }

    /**
     * 完成一次提交判题并经 sink 发事件；正常返回（含 CE/TLE 等业务终态）。
     * 基础设施故障抛 InfraBrokenException 进入重试路径；其余 Throwable 亦按可重试处置。
     */
    void execute(JudgeWork work, JudgeEventSink sink);
}
