package cn.hamm.spms.framework.http;

import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.framework.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * <h1>项目拥有的 HTTP 响应协议</h1>
 *
 * <p>保持 code/message/data，只有非 null 的 traceId 才输出；不是持久化 JSON codec。
 * 响应不可变，无静态可变 mapper 或错误单例状态。旧过滤链还在时，由显式兼容边界接回旧响应类型。</p>
 *
 * @author Hamm.cn
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;
    private final String traceId;

    private ApiResponse(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> of(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, null);
    }

    public static ApiResponse<Void> success() {
        return of(200, "操作成功", null);
    }

    public static ApiResponse<Void> success(String message) {
        return of(200, message, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return of(200, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return of(200, "操作成功", data);
    }

    public static <T> ApiResponse<T> data(T data) {
        return of(200, "获取成功", data);
    }

    public static <T> ApiResponse<T> data(T data, String message) {
        return of(200, message, data);
    }

    public static <T> ApiResponse<T> error(ErrorCode error, String message, T data) {
        return of(error.getCode(), message, data);
    }

    public static ApiResponse<Void> error(ErrorCode error, String message) {
        return error(error, message, null);
    }

    public static ApiResponse<Object> failure(BusinessException failure) {
        return of(failure.getCode(), failure.getMessage(), failure.getData());
    }

    public ApiResponse<T> withTraceId(String traceId) {
        return new ApiResponse<>(code, message, data, traceId);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getTraceId() { return traceId; }
}
