package cn.hamm.spms.module.asset.material.mapper;

import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionService;
import cn.hamm.spms.module.system.datapermission.service.ShadowDiffService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * <h1>物料 Mapper 读侧的 JPA shadow 对账接线</h1>
 *
 * <p>Mapper 业务出口不能再经过 JPA 的 {@code addSearchPredicate}，否则所谓"换轨"只是把两套
 * 查询都跑了一遍、结果却只返回 JPA。这个服务只在资源处于 SHADOW 时提交一次异步 COUNT：
 * 基线是物料表全量，guarded 是同一批策略树编译成 JPA Specification，Mapper 侧则由
 * {@link ShadowDiffService} 交给已登记的 {@code MaterialMapper} 探针。ENFORCE 不重复双跑，
 * OFF 不计算任何权限。</p>
 *
 * <p>策略树、JPA 编译器和 MyBatis 渲染器的输入都来自同一资源/策略快照；本类不拼第二份租户或
 * owner 条件。对账失败只写 ERROR/审计，不改变业务读响应。</p>
 *
 * @author Hamm.cn
 */
@Service
public class MaterialReadShadowService {
    public static final String RESOURCE_KEY = "asset.material";
    private static final DataPermissionAction ACTION = DataPermissionAction.QUERY;

    private final DataPermissionProperties properties;
    private final DataPermissionService dataPermissionService;
    private final PermissionRuleTreeBuilder ruleTreeBuilder;
    private final JpaPermissionRuleCompiler jpaRuleCompiler;
    private final ShadowDiffService shadowDiffService;
    private final DataPermissionAudit audit;

    public MaterialReadShadowService(DataPermissionProperties properties,
                                     DataPermissionService dataPermissionService,
                                     PermissionRuleTreeBuilder ruleTreeBuilder,
                                     JpaPermissionRuleCompiler jpaRuleCompiler,
                                     ShadowDiffService shadowDiffService,
                                     DataPermissionAudit audit) {
        this.properties = properties;
        this.dataPermissionService = dataPermissionService;
        this.ruleTreeBuilder = ruleTreeBuilder;
        this.jpaRuleCompiler = jpaRuleCompiler;
        this.shadowDiffService = shadowDiffService;
        this.audit = audit;
    }

    /**
     * 从当前请求提交 Material 的 JPA/Mapper COUNT 对账。
     *
     * <p>调用方只应在 Mapper 读出口调用；方法本身再次检查模式和上下文，避免未来新增出口时
     * 把 OFF/ENFORCE 语义误接到 shadow 队列。</p>
     */
    public void submit() {
        submit(null);
    }

    /**
     * 从当前请求提交带业务筛选的 Material JPA/Mapper COUNT 对账。
     *
     * @param filter 与真实 Mapper page/count/stat 相同的业务筛选；null 表示不筛选
     */
    public void submit(MaterialEntity filter) {
        if (properties.effectiveMode(RESOURCE_KEY) != DataPermissionProperties.Mode.SHADOW) {
            return;
        }
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            return;
        }
        MaterialEntity normalized = filter == null ? new MaterialEntity() : filter;
        try {
            DataResourceEntity resource = dataPermissionService.requireResource(RESOURCE_KEY);
            List<DataPolicyEntity> policies = dataPermissionService.getPolicies(RESOURCE_KEY, ACTION, context);
            List<PermissionRule> rules = ruleTreeBuilder.build(resource, policies, context).stream()
                    .map(PermissionRuleTreeBuilder.PolicyRule::rule)
                    .filter(rule -> rule != null)
                    .toList();
            if (rules.isEmpty()) {
                // 无策略的 shadow 读由拦截器保持原结果；没有树就没有可对账的 guarded 条件。
                audit.shadow(RESOURCE_KEY, "orm-parity:no-rules", "Material shadow 读没有可达 QUERY 策略");
                return;
            }
            Specification<MaterialEntity> business = businessFilter(normalized);
            Specification<MaterialEntity> permission = guarded(rules, context);
            shadowDiffService.compare(RESOURCE_KEY, ACTION, MaterialEntity.class, business, permission, context,
                    new ShadowDiffService.OrmParityProbe("material", rules, context,
                            Map.of("filter", normalized)));
        }
        catch (RuntimeException ex) {
            audit.binding(RESOURCE_KEY, "mapper-shadow-submit-failed", ex.getMessage());
        }
    }

    private Specification<MaterialEntity> businessFilter(MaterialEntity filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(builder.like(root.get("name"), "%" + filter.getName() + "%"));
            }
            if (filter.getCode() != null && !filter.getCode().isBlank()) {
                predicates.add(builder.like(root.get("code"), "%" + filter.getCode() + "%"));
            }
            if (filter.getMaterialType() != null) {
                predicates.add(builder.equal(root.get("materialType"), filter.getMaterialType()));
            }
            if (filter.getUseType() != null) {
                predicates.add(builder.equal(root.get("useType"), filter.getUseType()));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<MaterialEntity> guarded(List<PermissionRule> rules, PermissionContext context) {
        return (root, query, builder) -> {
            List<Predicate> predicates = rules.stream()
                    .map(rule -> jpaRuleCompiler.compile(rule, root, builder, new PermissionRuleContext(context)))
                    .filter(predicate -> predicate != null)
                    .toList();
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
