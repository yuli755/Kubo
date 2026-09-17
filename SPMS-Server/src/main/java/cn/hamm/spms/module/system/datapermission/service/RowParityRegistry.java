package cn.hamm.spms.module.system.datapermission.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import cn.hamm.spms.framework.curd.datapermission.RowParityCounter;

/**
 * <h1>哪些资源已经进入"两套 ORM 并存"的核对范围</h1>
 *
 * <p>它就是 {@link RowParityCounter} 的索引，别的一概不做。<b>判据不住在这里</b>：
 * "这条策略该拼哪个列"只由 {@code SubjectDimensionRules} 回答，本类只回答"这个资源有没有
 * Mapper 侧的眼睛可以对照"。</p>
 *
 * <p>为什么允许存在一张这样的表：它有两个真消费者（影子比对与放开前置检查），且<em>缺项是安全的</em> ——
 * 没登记的资源不产生对账样本，前置检查因此把它当"不适用"而不是"通过"。这与"注册了却没人读"的
 * 登记表正好相反，那种表在本仓是要删的东西。</p>
 *
 * @author Hamm.cn
 */
public final class RowParityRegistry {

    private final Map<String, RowParityCounter> counters;

    /**
     * @param probes 各资源的探针；<b>同一资源两个探针</b>直接启动失败（静默取第一个的话，
     *               两个 Mapper 各数一张表、谁也不知道对账用的是哪一个）
     */
    public RowParityRegistry(Collection<RowParityCounter> probes) {
        Map<String, RowParityCounter> index = new LinkedHashMap<>();
        for (RowParityCounter probe : probes == null ? Set.<RowParityCounter>of() : probes) {
            if (probe == null) {
                continue;
            }
            String resourceKey = probe.parityResourceKey();
            if (resourceKey == null || resourceKey.isBlank()) {
                throw new IllegalStateException("RowParityCounter " + probe.getClass().getName()
                        + " declares a blank parityResourceKey");
            }
            RowParityCounter previous = index.putIfAbsent(resourceKey, probe);
            if (previous != null) {
                throw new IllegalStateException("duplicated RowParityCounter for resource " + resourceKey + ": "
                        + previous.getClass().getName() + " and " + probe.getClass().getName());
            }
        }
        this.counters = Map.copyOf(index);
    }

    /**
     * 该资源的 Mapper 侧探针
     *
     * @param resourceKey 资源标识
     * @return 探针；未登记返回 {@code null}（调用方必须把"没有"读成"无证据"，不是"一致"）
     */
    public RowParityCounter counterFor(String resourceKey) {
        return resourceKey == null ? null : counters.get(resourceKey);
    }

    /**
     * 已登记对账的资源清单（前置检查用它区分"不适用"与"缺证据"）
     *
     * @return 资源标识集合（不可变）
     */
    public Set<String> resources() {
        return counters.keySet();
    }

    /**
     * 是否一个探针都没有（{@code mapper-read} 关着时的常态）
     *
     * @return 没有任何资源进入对账范围返回 true
     */
    public boolean isEmpty() {
        return counters.isEmpty();
    }
}
