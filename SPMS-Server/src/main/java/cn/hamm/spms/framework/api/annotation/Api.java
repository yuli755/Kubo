package cn.hamm.spms.framework.api.annotation;

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

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>API 控制器</h1>
 *
 * @author Hamm.cn
 */
@Target(TYPE)
@Retention(RUNTIME)
@Inherited
@Documented
@RestController
@RequestMapping
public @interface Api {
    /**
     * API 的路径
     *
     * @see RequestMapping#path()
     */
    @SuppressWarnings("UnusedReturnValue")
    @AliasFor(annotation = RequestMapping.class, attribute = "path")
    String value();
}
