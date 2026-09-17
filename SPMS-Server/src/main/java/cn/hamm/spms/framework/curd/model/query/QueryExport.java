package cn.hamm.spms.framework.curd.model.query;

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

import cn.hamm.spms.core.RootModel;
import cn.hamm.spms.core.annotation.Description;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <h1>查询导出结果模型</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QueryExport extends RootModel<QueryExport> {
    @NotBlank(message = "文件 Code 不能为空")
    @Description("文件 Code")
    private String fileCode;
}
