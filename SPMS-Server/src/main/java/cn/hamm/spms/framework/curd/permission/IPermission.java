package cn.hamm.spms.framework.curd.permission;

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

import cn.hamm.spms.core.interfaces.ITree;

/**
 * <h1>权限接口</h1>
 *
 * @author Hamm
 * @see PermissionUtil#scanPermission
 */
public interface IPermission<E extends IPermission<E>> extends ITree<E> {
    /**
     * 获取权限的名称
     *
     * @return 权限名称
     */
    String getName();

    /**
     * 设置权限名称
     *
     * @param name 权限名称
     * @return 权限实体
     */
    E setName(String name);

    /**
     * 获取权限标识
     *
     * @return 权限标识
     */
    String getIdentity();

    /**
     * 设置权限标识
     *
     * @param identity 权限标识
     * @return 权限实体
     */
    E setIdentity(String identity);
}
