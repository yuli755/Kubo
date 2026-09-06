package cn.hamm.spms.module.system.datapermission.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <h1>数据权限运行模式配置</h1>
 *
 * <p>对应《供应链权限治理二次改造审计与实施计划》阶段 7「观察模式 → 强制模式」的灰度要求。
 * 之前代码是「编译期硬编码 fail-open」，既不能灰度也不能回滚；本配置把三态开关做实。</p>
 *
 * <pre>{@code
 * kubo:
 *   data-permission:
 *     mode: shadow                 # off | shadow | enforce
 *     enforce-resources:           # 逐资源灰度名单（见下方 enforceResources 说明）
 *       - wms.inventory
 *       - mes.order
 *     deny-unregistered: false     # enforce 下未注册资源是否默认拒绝（阶段 7 末尾改 true）
 *     tenant-required: false       # 用户无租户归属时是否直接拒绝（回填完成后改 true）
 *     default-tenant-id: 1         # tenant-required=false 时的兜底租户（平台默认租户）
 *     shadow-diff-log: true        # 观察模式是否输出「本应被过滤」的明细日志
 * }</pre>
 *
 * @author Hamm.cn
 */
@Data
@Component
@ConfigurationProperties(prefix = "kubo.data-permission")
public class DataPermissionProperties {

    /**
     * 运行模式
     */
    public enum Mode {
        /** 完全不参与（行为等价于改造前，用于回滚） */
        OFF,
        /** 只计算与记录，不改变查询结果（灰度观察） */
        SHADOW,
        /** 真正注入过滤条件与拒绝写入 */
        ENFORCE
    }

    /**
     * 全局模式，默认 {@link Mode#SHADOW}：
     * 上线这一步不改变任何业务结果，但能立刻暴露"开了 enforce 会发生什么"。
     */
    private Mode mode = Mode.SHADOW;

    /**
     * 资源白名单，用于逐资源灰度：
     * <ul>
     *   <li>{@code mode=enforce} —— 名单为空表示全量生效；非空则只有名单内资源 enforce，其余降为 shadow；</li>
     *   <li>{@code mode=shadow} —— 名单内资源<b>提前</b>进入 enforce（观察够了就先放开一个）。</li>
     * </ul>
     */
    private Set<String> enforceResources = new LinkedHashSet<>();

    /**
     * enforce 模式下，未注册 / 未启用的资源是否默认拒绝（fail-closed）。
     */
    private boolean denyUnregistered = false;

    /**
     * 用户没有任何租户归属时是否拒绝请求。
     * 迁移回填完成前保持 false（回落 {@link #defaultTenantId}），回填后置 true。
     */
    private boolean tenantRequired = false;

    /**
     * 平台默认租户 ID（与 {@code tenant} 表的 code=default 记录一致）。
     */
    private Long defaultTenantId = 1L;

    /**
     * 观察模式是否输出差异明细日志。
     */
    private boolean shadowDiffLog = true;

    /**
     * 是否审计「明文敏感字段访问」。
     */
    private boolean auditPlaintextAccess = true;

    /**
     * 判断指定资源的生效模式
     *
     * @param resourceKey 资源标识
     * @return OFF / SHADOW / ENFORCE
     */
    public Mode effectiveMode(String resourceKey) {
        if (mode == Mode.OFF) {
            return Mode.OFF;
        }
        if (mode == Mode.ENFORCE) {
            // 全量 enforce 时，白名单是"例外清单"：名单外（非空时）先降回 shadow
            return enforceResources.isEmpty() || enforceResources.contains(resourceKey)
                    ? Mode.ENFORCE : Mode.SHADOW;
        }
        // 全局 shadow 时，白名单是"提前放开清单"：名单内资源先 enforce
        return enforceResources.contains(resourceKey) ? Mode.ENFORCE : Mode.SHADOW;
    }

    /**
     * 该资源是否会真正改变查询/写入结果
     *
     * @param resourceKey 资源标识
     * @return 是否强制
     */
    public boolean enforced(String resourceKey) {
        return effectiveMode(resourceKey) == Mode.ENFORCE;
    }

    /**
     * 该资源是否需要计算权限条件（OFF 时全部短路）
     *
     * @param resourceKey 资源标识
     * @return 是否参与计算
     */
    public boolean computes(String resourceKey) {
        return effectiveMode(resourceKey) != Mode.OFF;
    }
}
