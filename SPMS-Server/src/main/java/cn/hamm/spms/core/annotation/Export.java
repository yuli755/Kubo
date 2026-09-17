package cn.hamm.spms.core.annotation;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>Excel 导出列</h1>
 *
 * @author Hamm.cn
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Inherited
@Documented
public @interface Export {
    /**
     * 列数据类型
     */
    Type value() default Type.TEXT;

    /**
     * 导出列排序
     */
    int sort() default 0;

    /**
     * 是否移除
     */
    boolean remove() default false;

    /**
     * <h1>列数据类型</h1>
     *
     * @author Hamm.cn
     */
    enum Type {
        /**
         * 普通文本
         */
        TEXT,

        /**
         * 时间日期
         */
        DATETIME,

        /**
         * 数字
         */
        NUMBER,

        /**
         * 字典
         *
         * @apiNote 请确保同时标记了 {@link Dictionary}
         */
        DICTIONARY,

        /**
         * 布尔值
         */
        BOOLEAN
    }
}
