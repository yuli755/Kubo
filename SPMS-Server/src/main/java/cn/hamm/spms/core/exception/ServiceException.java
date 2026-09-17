package cn.hamm.spms.core.exception;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.interfaces.IException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>系统异常包装类</h1>
 *
 * @author Hamm.cn
 */
@NoArgsConstructor
@Getter
public class ServiceException extends RuntimeException implements IException<ServiceException> {
    /**
     * 错误代码
     */
    private int code = Json.SERVICE_ERROR;

    /**
     * 错误数据
     */
    private Object data = null;

    /**
     * 抛出一个自定义错误信息的默认异常
     *
     * @param message 错误信息
     */
    public ServiceException(String message) {
        super(message);
    }

    /**
     * 抛出一个自定义错误信息的默认异常
     *
     * @param message 错误信息
     * @param data    错误数据
     */
    public ServiceException(String message, Object data) {
        super(message);
        this.data = data;
    }

    /**
     * 抛出一个自定义错误信息的默认异常
     *
     * @param code    错误代码
     * @param message 错误信息
     */
    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 抛出一个自定义错误信息的默认异常
     *
     * @param code    错误代码
     * @param message 错误信息
     * @param data    错误数据
     */
    public ServiceException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    /**
     * 直接抛出一个异常
     *
     * @param exception 异常
     * @param message   错误信息
     */
    public ServiceException(@NotNull IException<?> exception, String message) {
        super(message);
        this.code = exception.getCode();
    }

    /**
     * 直接抛出一个异常
     *
     * @param exception 异常
     */
    public ServiceException(@NotNull IException<?> exception) {
        super(exception.getMessage());
        this.code = exception.getCode();
    }
}
