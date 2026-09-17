package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 B2：整文件搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core，
 * 逻辑一字未改；本仓从此**自己拥有**这一层（上游变化不再自动进入）。改代码请改这里。
 * 为什么只搬这些：按 import 关系算出的**真实调用闭包**——本项目不用的（AesUtil/RsaUtil/HttpUtil/
 * HostUtil/ValidateUtil/DesensitizeUtil 等）一律不搬；上游其余部分仍由 airpower 依赖带着。
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
