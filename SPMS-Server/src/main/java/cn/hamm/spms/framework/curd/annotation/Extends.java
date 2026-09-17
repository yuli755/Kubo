package cn.hamm.spms.framework.curd.annotation;

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

import cn.hamm.spms.framework.curd.base.Curd;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>继承的接口 {@code 白名单优先}</h1>
 *
 * <li>如不标记此注解,则默认将所有基类接口继承</li>
 * <li>如标记白名单,则需写全需要继承的接口</li>
 * <li>如标记黑名单,则只需要写不继承的接口</li>
 *
 * @author Hamm.cn
 * @see Curd
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface Extends {
    /**
     * 排除父类接口但需要继承这些接口
     */
    Curd[] value() default {};

    /**
     * 继承父类接口但排除这些接口
     */
    Curd[] exclude() default {};
}
