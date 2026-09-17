package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.constant.HttpConstant;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * <h1>Trace 工具类</h1>
 *
 * @author Hamm.cn
 */
public class TraceUtil {

    /**
     * 重置 TraceID
     */
    public static void resetTraceId() {
        setTraceId(null);
    }

    /**
     * 获取 TraceID
     *
     * @return TraceID
     */
    public static String getTraceId() {
        return MDC.get(HttpConstant.Header.TRACE_ID);
    }

    /**
     * 设置 TraceID
     *
     * @param traceId TraceID
     */
    public static void setTraceId(@Nullable String traceId) {
        if (!StringUtil.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(HttpConstant.Header.TRACE_ID, traceId);
    }
}
