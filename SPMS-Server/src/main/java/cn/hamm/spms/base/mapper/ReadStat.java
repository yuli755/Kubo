package cn.hamm.spms.base.mapper;

import lombok.Data;

/**
 * <h1>读侧统计结果（十四资源统一口径）</h1>
 *
 * <p>受保护资源的 {@code stat} 读出口统一返回本形状：只含行数与已发布行数，不做任何
 * 敏感字段（价格/金额等）的聚合，避免把聚合结果暴露成绕过列级脱敏的旁路。十四资源
 * 共用同一份，前端可消费同一统计契约。</p>
 *
 * <p>单据类资源（入库/出库/移库/采购/销售）的生命周期由单据状态（status）驱动，
 * {@code is_published} 多数情况为 false，故其 {@code publishedTotal} 通常为 0；保留该列是为了
 * 让十四个资源的统计形状一致，而不是声称单据有"发布"语义。</p>
 *
 * @author Hamm.cn
 */
@Data
public class ReadStat {
    private long total;
    private long publishedTotal;
}
