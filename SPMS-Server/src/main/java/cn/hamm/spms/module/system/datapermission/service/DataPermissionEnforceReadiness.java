package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h1>enforce 配置的启动期闸门</h1>
 *
 * <p>{@code enforce-readiness} 之前只是一个管理端报告：管理员看见红灯后仍然可以把资源写进
 * {@code enforce-resources}，然后由线上真实请求替他发现遗漏。这一格把报告升级成配置变更门禁：
 * 只要某个资源实际会进入 {@code ENFORCE}，启动期就必须能在同一份 readiness 判据里找到它，
 * 且所有阻塞项都通过；否则应用不启动。</p>
 *
 * <p>没有另加一个"跳过校验"开关。{@code mode=OFF}，或 {@code mode=SHADOW} 且名单为空时，
 * 本闸门零查询、零日志；真正写入 enforce 配置才触发校验。再加一个 bypass 开关只会把"红着开闸"
 * 变成配置文件里一行不显眼的 false，和本模块"凡是放宽都默认关且可审计"的纪律相冲突。</p>
 *
 * <p>校验挂在 {@link ApplicationReadyEvent} 而不是 Bean 构造阶段：readiness 需要 JPA 元模型、资源表、
 * 策略表和两张证据表；这些依赖在 Bean 创建时可能还没有完成初始化。抛异常而不是只告警，是因为
 * 继续启动会把"配置明确要求 enforce、但证据不够"伪装成可用服务。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionEnforceReadiness {

    private final DataPermissionProperties properties;

    private final DataResourceService resourceService;

    public DataPermissionEnforceReadiness(DataPermissionProperties properties,
                                          DataResourceService resourceService) {
        this.properties = properties;
        this.resourceService = resourceService;
    }

    /**
     * 配置实际会改变业务结果时，启动前必须通过 readiness。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        if (!requiresValidation(properties)) {
            return;
        }
        List<DataResourceService.Readiness> readiness = resourceService.readinessAll();
        List<String> problems = validate(properties, readiness);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "kubo.data-permission 的 enforce 配置未通过 enforce-readiness，应用拒绝启动：\n  - "
                            + String.join("\n  - ", problems)
                            + "\n请先访问 /dataResource/enforce-readiness 修复阻塞项，再把资源加入 enforce-resources；"
                            + "不要用额外 bypass 开关绕过这道门。"
            );
        }
    }

    /**
     * 默认/回滚档的短路判定；这些档位不能为了"校验一下"而访问数据库。
     *
     * @param properties 数据权限配置
     * @return 当前配置是否真的会让某个资源进入 enforce
     */
    static boolean requiresValidation(DataPermissionProperties properties) {
        if (properties == null || properties.getMode() == null
                || properties.getMode() == DataPermissionProperties.Mode.OFF) {
            return false;
        }
        return properties.getMode() == DataPermissionProperties.Mode.ENFORCE
                || (properties.getEnforceResources() != null && !properties.getEnforceResources().isEmpty());
    }

    /**
     * 纯配置/报告判定，供单测直接验证，不访问 Spring 或数据库。
     *
     * <p>名单语义与 {@link DataPermissionProperties#effectiveMode(String)} 保持一致：</p>
     * <ul>
     *   <li>{@code mode=OFF}：名单不生效，跳过；</li>
     *   <li>{@code mode=SHADOW}：只有非空名单里的资源提前进入 enforce，校验名单中的每个键；</li>
     *   <li>{@code mode=ENFORCE} 且名单非空：名单是实际 enforce 的资源集合，校验名单中的每个键；</li>
     *   <li>{@code mode=ENFORCE} 且名单为空：全部已登记资源实际 enforce，校验 readiness 返回的全部资源。</li>
     * </ul>
     *
     * @param properties 数据权限配置
     * @param readiness  已登记资源的 readiness 报告
     * @return 阻塞问题；空表示当前配置可以启动
     */
    static List<String> validate(DataPermissionProperties properties,
                                 Collection<DataResourceService.Readiness> readiness) {
        if (properties == null || properties.getMode() == null
                || properties.getMode() == DataPermissionProperties.Mode.OFF) {
            return List.of();
        }

        List<String> problems = new ArrayList<>();
        Set<String> configured = configuredKeys(properties.getEnforceResources(), problems);
        Map<String, DataResourceService.Readiness> byKey = new LinkedHashMap<>();
        if (readiness != null) {
            for (DataResourceService.Readiness item : readiness) {
                if (item == null || item.resourceKey() == null || item.resourceKey().isBlank()) {
                    problems.add("readiness 返回了空资源键：不能把未知资源当成已通过");
                    continue;
                }
                DataResourceService.Readiness previous = byKey.putIfAbsent(item.resourceKey(), item);
                if (previous != null) {
                    problems.add("readiness 对资源 " + item.resourceKey() + " 返回了重复结论：拒绝用不确定的结果启动");
                }
            }
        }

        Set<String> targets = new LinkedHashSet<>();
        if (properties.getMode() == DataPermissionProperties.Mode.ENFORCE && configured.isEmpty()) {
            targets.addAll(byKey.keySet());
        }
        else {
            targets.addAll(configured);
        }

        for (String resourceKey : targets) {
            DataResourceService.Readiness item = byKey.get(resourceKey);
            if (item == null) {
                problems.add(resourceKey + " 出现在 enforce-resources，但 data_resource 未登记或 readiness 没有返回它");
                continue;
            }
            if (!item.ready()) {
                String detail = item.blockers() == null || item.blockers().isEmpty()
                        ? "readiness=not-ready，但没有返回阻塞原因（判据输出不完整）"
                        : String.join("；", item.blockers());
                problems.add(resourceKey + " 未通过 enforce-readiness：" + detail);
            }
        }
        return List.copyOf(problems);
    }

    private static Set<String> configuredKeys(Collection<String> raw,
                                              List<String> problems) {
        Set<String> keys = new LinkedHashSet<>();
        if (raw == null) {
            return keys;
        }
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                problems.add("enforce-resources 含空资源键：不能把空值解释成全量放开");
                continue;
            }
            if (!keys.add(value)) {
                problems.add("enforce-resources 含重复资源键 " + value + "：请保留一份，避免审计记录与配置漂移");
            }
        }
        return keys;
    }
}
