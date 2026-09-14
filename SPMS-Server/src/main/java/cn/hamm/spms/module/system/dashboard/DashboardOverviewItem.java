package cn.hamm.spms.module.system.dashboard;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>业务概况条目（P2 · 数据权限感知看板）</h1>
 *
 * <p>每个资源一行：{@code resourceKey} 与后端 {@code @DataResource(key)} 同域（便于前端对账），
 * {@code label} 是人看的名称，{@code count} 是<b>当前用户可见范围内</b>的行数 —— 由各资源的
 * {@code getMapperCount} 经 Mapper 权限拦截链得出，不新增任何旁路统计。</p>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class DashboardOverviewItem {
    /** 数据资源标识，如 {@code channel.purchase} */
    private String resourceKey;
    /** 人看名称 */
    private String label;
    /** 当前用户可见行数 */
    private long count;
}
