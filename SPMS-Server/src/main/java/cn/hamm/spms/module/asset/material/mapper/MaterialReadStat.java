package cn.hamm.spms.module.asset.material.mapper;

import lombok.Data;

/**
 * <h1>物料读侧统计结果</h1>
 *
 * <p>统计只返回行数与已发布行数，不把采购/销售价格的聚合结果暴露成绕过字段脱敏的旁路。</p>
 *
 * @author Hamm.cn
 */
@Data
public class MaterialReadStat {
    private long total;
    private long publishedTotal;
}
