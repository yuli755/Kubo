package cn.hamm.spms.framework.curd.base;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

/**
 * <h1>实体的操作标准接口</h1>
 *
 * @author Hamm.cn
 */
public interface ICurdAction {
    /**
     * ID 必须传入的场景
     */
    interface WhenIdRequired {
    }

    /**
     * 当添加时
     */
    interface WhenAdd {
    }

    /**
     * 当更新时
     */
    interface WhenUpdate {
    }
}
