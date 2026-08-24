package com.mashangping.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：HTTP 200 + 业务错误码 */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        return ApiResponse.fail(e.getErrorCode(), e.getMessage());
    }

    /** 参数校验失败：取第一个字段错误的提示 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数不合法" : fe.getField() + " " + fe.getDefaultMessage();
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, msg);
    }

    /** 客户端提交了无法解析的请求体（如畸形 JSON）：归为参数错误而非系统故障 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    /** 请求路径不存在：归为资源不存在而非系统故障 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        return ApiResponse.fail(ErrorCode.NOT_FOUND, "资源不存在");
    }

    /** 请求方法不支持（如对 GET-only 端点发 DELETE）：归为参数错误而非系统故障 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求方法不支持");
    }

    /** 方法级鉴权(@PreAuthorize)拒绝：必须重新抛出，交由安全层
     *  ExceptionTranslationFilter→RestProblemHandling 输出真实 HTTP 403；
     *  若被兜底捕获会误标为 code=50000"系统繁忙"。 */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e) throws AccessDeniedException {
        throw e;
    }

    /** 兜底：记日志，对外只说"系统繁忙" */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknown(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR);
    }
}
