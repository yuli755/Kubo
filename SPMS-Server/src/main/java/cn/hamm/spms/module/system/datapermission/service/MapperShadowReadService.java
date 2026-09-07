package cn.hamm.spms.module.system.datapermission.service;

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
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * <h1>Mapper 读侧 shadow 对账公共接线</h1>
 *
 * <p>每个业务资源只负责提供自己的业务筛选 Specification 和 Mapper 参数；资源、主体策略、
 * 条件树、JPA 编译器和 ORM parity 探针的接线集中在这里。这样新增资源时不会复制第二套
 * "谁能看哪些行"判据，也不会因为忘记接某一段而只返回 Mapper、却没有 JPA 对账证据。</p>
 *
 * <p>本服务只在 SHADOW 模式工作。它捕获观测链异常并记审计，不把异步观测故障传播到业务读响应；
 * ENFORCE 的实际拒答仍由 JPA Specification 或 MyBatis 拦截器负责。</p>
 *
 * @author Hamm.cn
 */
@Service
public class MapperShadowReadService {
    private final DataPermissionProperties properties;
    private final DataPermissionService dataPermissionService;
    private final PermissionRuleTreeBuilder ruleTreeBuilder;
    private final JpaPermissionRuleCompiler jpaRuleCompiler;
    private final ShadowDiffService shadowDiffService;
    private final DataPermissionAudit audit;

    public MapperShadowReadService(DataPermissionProperties properties,
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
     * 提交一次 Mapper 读侧的 JPA/Mapper parity 对账。
     *
     * @param resourceKey 资源键
     * @param tableAlias 资源登记的物理表名（也是权限片段限定名）
     * @param entityClass JPA 实体类型
     * @param businessFilter 与真实 Mapper 语句相同的业务筛选；null 表示无业务筛选
     * @param statementParameters 资源自有的 Mapper 参数；权限 fragment 不接受外部覆盖
     */
    public void submit(String resourceKey,
                       String tableAlias,
                       Class<?> entityClass,
                       Specification<?> businessFilter,
                       Map<String, Object> statementParameters) {
        if (properties.effectiveMode(resourceKey) != DataPermissionProperties.Mode.SHADOW) {
            return;
        }
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            return;
        }
        try {
            DataResourceEntity resource = dataPermissionService.requireResource(resourceKey);
            List<DataPolicyEntity> policies = dataPermissionService.getPolicies(
                    resourceKey, DataPermissionAction.QUERY, context);
            List<PermissionRule> rules = ruleTreeBuilder.build(resource, policies, context).stream()
                    .map(PermissionRuleTreeBuilder.PolicyRule::rule)
                    .filter(rule -> rule != null)
                    .toList();
            if (rules.isEmpty()) {
                audit.shadow(resourceKey, "orm-parity:no-rules", "shadow 读没有可达 QUERY 策略");
                return;
            }
            Specification<?> permissionFilter = permission(rules, context);
            shadowDiffService.compare(resourceKey, DataPermissionAction.QUERY, entityClass,
                    businessFilter, permissionFilter, context,
                    new ShadowDiffService.OrmParityProbe(tableAlias, rules, context, statementParameters));
        }
        catch (RuntimeException ex) {
            audit.binding(resourceKey, "mapper-shadow-submit-failed", ex.getMessage());
        }
    }

    private Specification<?> permission(List<PermissionRule> rules, PermissionContext context) {
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
