package cn.hamm.spms.core.interfaces;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.ReflectUtil;

/**
 * <h1>枚举字典标准接口</h1>
 *
 * @author Hamm.cn
 * @see ReflectUtil
 */
public interface IDictionary {
    /**
     * 获取枚举的 Key
     *
     * @return Key
     */
    int getKey();

    /**
     * 获取枚举的描述
     *
     * @return 描述
     */
    String getLabel();

    /**
     * 判断 Key 是否相等
     *
     * @param key 被判断的 Key
     * @return 对比结果
     */
    default boolean equalsKey(int key) {
        return getKey() == key;
    }

    /**
     * 判断 Key 是否不相等
     *
     * @param key 被判断的 Key
     * @return 对比结果
     */
    default boolean notEqualsKey(int key) {
        return !equalsKey(key);
    }
}
