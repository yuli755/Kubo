package cn.hamm.spms.framework.curd.config;

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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static cn.hamm.spms.framework.curd.base.CurdEntity.STRING_ID;

/**
 * <h1>全局默认配置文件</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("spms.curd")
public class CurdConfig {
    /**
     * 默认分页条数
     */
    private int defaultPageSize = 20;

    /**
     * 默认排序字段
     */
    private String defaultSortField = STRING_ID;
}
