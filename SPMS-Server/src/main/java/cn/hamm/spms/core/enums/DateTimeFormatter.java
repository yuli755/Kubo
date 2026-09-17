package cn.hamm.spms.core.enums;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.DateTimeUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * <h1>格式化模板</h1>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum DateTimeFormatter {
    /**
     * 年
     */
    YEAR("yyyy"),

    /**
     * 月
     */
    MONTH("MM"),

    /**
     * 日
     */
    DAY("dd"),

    /**
     * 时
     */
    HOUR("HH"),

    /**
     * 分
     */
    MINUTE("mm"),

    /**
     * 秒
     */
    SECOND("ss"),

    /**
     * 年月日
     */
    FULL_DATE("yyyy-MM-dd"),

    /**
     * 时分秒
     */
    FULL_TIME("HH:mm:ss"),

    /**
     * 年月日时分秒
     */
    FULL_DATETIME("yyyy-MM-dd HH:mm:ss"),

    /**
     * 月日时分
     */
    SHORT_DATETIME("MM-dd HH:mm"),
    ;

    private final String value;

    /**
     * 使用这个模板格式化毫秒时间戳
     *
     * @param milliSecond 毫秒时间戳
     * @return 格式化后的字符串
     */
    public final @NotNull String format(long milliSecond) {
        return DateTimeUtil.format(milliSecond, this);
    }

    /**
     * 使用这个模板格式化当前时间
     *
     * @return 格式化后的字符串
     */
    public final @NotNull String formatCurrent() {
        return format(System.currentTimeMillis());
    }
}
