package com.mashangping.ws;

import com.mashangping.judging.JudgeProgressPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** 定向推送：convertAndSendToUser(name=uid, /queue/judge-progress, event) */
@Component
@RequiredArgsConstructor
public class StompJudgeProgressPublisher implements JudgeProgressPublisher {

    private final SimpMessagingTemplate messaging;

    private void send(long userId, JudgeProgressEvent e) {
        try {
            messaging.convertAndSendToUser(String.valueOf(userId),
                    "/queue/judge-progress", e);
        } catch (Exception ignored) {
            // 推送是尽力而为的通知面；会话缺失/网络异常不打断判题主流程
        }
    }

    @Override
    public void judging(long userId, long submissionId) {
        send(userId, new JudgeProgressEvent("JUDGING", submissionId,
                null, null, null, null, null, null, null));
    }

    @Override
    public void point(long userId, long submissionId, int pointIndex, String status) {
        send(userId, new JudgeProgressEvent("POINT", submissionId,
                pointIndex, status, null, null, null, null, null));
    }

    @Override
    public void compileError(long userId, long submissionId, String message) {
        send(userId, new JudgeProgressEvent("COMPILE_ERROR", submissionId,
                null, null, message, null, null, null, null));
    }

    @Override
    public void finished(long userId, long submissionId, String status,
                         Integer score, int passedCount, int totalCount) {
        send(userId, new JudgeProgressEvent("FINISHED", submissionId,
                null, null, null, status, score, passedCount, totalCount));
    }
}
