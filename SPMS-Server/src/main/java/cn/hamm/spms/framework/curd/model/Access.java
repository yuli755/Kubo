package cn.hamm.spms.framework.curd.model;

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

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>权限控制配置类</h1>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class Access {
    /**
     * 需要登录
     */
    private boolean login = false;

    /**
     * 需要授权访问
     */
    private boolean authorize = false;
}
