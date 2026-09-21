package cn.hamm.spms.framework.util;

import java.util.UUID;

/**
 * <h1>全链路追踪标识持有器</h1>
 *
 * <p>纯 JDK 17 实现，基于 ThreadLocal 管理当前请求线程的链路追踪 ID（{@code X-Trace-ID}）。
 * 替代已退场的旧 {@code airpower.core.TraceUtil}。</p>
 *
 * @author Hamm.cn
 */
public final class Tracer {
    public static final String TRACE_HEADER = "X-Trace-ID";
    private static final ThreadLocal<String> TRACE_HOLDER = new ThreadLocal<>();

    private Tracer() {
    }

    public static String getTraceId() {
        return TRACE_HOLDER.get();
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.trim().isEmpty()) {
            TRACE_HOLDER.set(traceId.trim());
        } else {
            TRACE_HOLDER.set(UUID.randomUUID().toString().replace("-", ""));
        }
    }

    public static void clear() {
        TRACE_HOLDER.remove();
    }
}
