package cn.hamm.spms.core.enums;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>脱敏方式</h1>
 *
 * @author Hamm.cn
 */
@AllArgsConstructor
@Getter
public enum DesensitizeType {
    /**
     * 座机号码
     */
    TELEPHONE(0, 0),

    /**
     * 手机号码
     */
    MOBILE(3, 4),

    /**
     * 身份证号
     */
    ID_CARD(6, 4),

    /**
     * 银行卡号
     */
    BANK_CARD(4, 4),

    /**
     * 车牌号
     */
    CAR_NUMBER(2, 1),

    /**
     * 邮箱
     */
    EMAIL(2, 2),

    /**
     * 中文名
     */
    CHINESE_NAME(1, 1),

    /**
     * 地址
     */
    ADDRESS(3, 0),

    /**
     * IPv4 地址
     */
    IP_V4(0, 0),

    /**
     * 自定义
     */
    CUSTOM(0, 0);

    /**
     * 开始至少保留
     */
    private final int minHead;

    /**
     * 结束至少保留
     */
    private final int minTail;
}
