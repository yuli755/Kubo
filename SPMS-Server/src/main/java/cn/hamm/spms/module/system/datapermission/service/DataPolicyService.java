package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>数据权限策略管理服务</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class DataPolicyService extends BaseService<DataPolicyEntity, DataPolicyRepository> {
    private final DataPermissionEventPublisher eventPublisher;
    private final DataResourceRepository resourceRepository;
    private final PermissionRuleParser ruleParser;

    public DataPolicyService(DataPermissionEventPublisher eventPublisher,
                             DataResourceRepository resourceRepository,
                             PermissionRuleParser ruleParser) {
        this.eventPublisher = eventPublisher;
        this.resourceRepository = resourceRepository;
        this.ruleParser = ruleParser;
    }

    /**
     * 保存前校验并规范化 {@code condition_json}
     *
     * <p>把"禁止裸 SQL、列必须在白名单、深度/数量上限"这些规则前移到写入侧：
     * 否则运维可以存进一条永远不会生效、也可能永远不会报错的条件，
     * 读链路只能事后从 binding 指标里发现。</p>
     */
    @Override
    protected DataPolicyEntity beforeAdd(DataPolicyEntity entity) {
        validateDimensions(entity);
        validateCondition(entity);
        return super.beforeAdd(entity);
    }

    @Override
    protected DataPolicyEntity beforeUpdate(DataPolicyEntity entity) {
        validateDimensions(entity);
        validateCondition(entity);
        return super.beforeUpdate(entity);
    }

    /**
     * 拒绝"配了也白配"的策略（见 {@link DimensionGuard}）
     *
     * <p>为什么卡在写入侧而不是读侧：读侧最多记一条 binding 指标，运维要翻日志才发现
     * 防护其实没生效；而保存成功的那一刻，管理员的心智模型已经是"这条数据被限住了"。</p>
     */
    private void validateDimensions(DataPolicyEntity entity) {
        String subjectReason = DimensionGuard.rejectUnavailableSubject(entity.getSubjectType());
        if (subjectReason != null) {
            PARAM_INVALID.show(subjectReason);
            return;
        }
        String conditionReason = DimensionGuard.rejectUnavailableCondition(entity.getConditionJson());
        if (conditionReason != null) {
            PARAM_INVALID.show(conditionReason);
        }
    }

    private void validateCondition(DataPolicyEntity entity) {
        if (entity.getConditionJson() == null || entity.getConditionJson().isBlank()) {
            entity.setConditionJson("{}");
            return;
        }
        DataResourceEntity resource = entity.getResourceId() == null
                ? null : resourceRepository.findById(entity.getResourceId()).orElse(null);
        String resourceKey = resource != null ? resource.getResourceKey() : ("resourceId=" + entity.getResourceId());
        PermissionRuleValidator.FieldAllowList allowList = allowListOf(resource);
        PermissionRule rule;
        try {
            rule = ruleParser.parse(resourceKey, entity.getConditionJson());
            new PermissionRuleValidator(allowList).validate(resourceKey, rule);
        }
        catch (RuntimeException ex) {
            PARAM_INVALID.show(ex.getMessage());
            return;
        }
        if (!rule.contributes()) {
            entity.setConditionJson("{}");
        }
    }

    /**
     * 按资源取全部启用中的行级策略（跨动作），管理端盘点用
     *
     * <p>刻意<b>不</b>走 {@code JpaPermissionRuleProvider} 那份缓存：它的键是
     * {@code resourceId + action.name()}，而盘点要看的正是"不在任何合法动作里"的行。
     * 塞进同一缓存会凭空多出第三种键语义，收益只是省一次几十行的表扫描。</p>
     *
     * @param resourceId 数据资源 ID
     * @return 启用中的策略，按 priority 降序；无则空列表
     */
    public List<DataPolicyEntity> getEnabledPoliciesByResource(Long resourceId) {
        if (resourceId == null) {
            return List.of();
        }
        List<DataPolicyEntity> policies = repository.findByResourceIdAndEnabledTrueOrderByPriorityDesc(resourceId);
        return policies != null ? policies : List.of();
    }

    private PermissionRuleValidator.FieldAllowList allowListOf(DataResourceEntity resource) {
        Set<String> columns = new LinkedHashSet<>();
        if (resource != null) {
            addCsv(columns, resource.getTenantColumn());
            addCsv(columns, resource.getOwnerColumns());
            addCsv(columns, resource.getFilterableColumns());
        }
        return new PermissionRuleValidator.FieldAllowList(columns);
    }

    private void addCsv(Set<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                target.add(part.trim());
            }
        }
    }

    @Override
    protected void afterAppAdd(long id, DataPolicyEntity entity) {
        super.afterAppAdd(id, entity);
        publishPolicyEvent(entity);
    }

    @Override
    protected void afterAppUpdate(long id, DataPolicyEntity entity) {
        super.afterAppUpdate(id, entity);
        publishPolicyEvent(entity);
    }

    @Override
    protected void beforeDelete(DataPolicyEntity entity) {
        super.beforeDelete(entity);
        // 在删除前发布事件
        publishPolicyEvent(entity);
    }

    private void publishPolicyEvent(DataPolicyEntity policy) {
        String resourceKey = getResourceKey(policy.getResourceId());
        if (resourceKey != null) {
            Set<DataPermissionAction> actions = Set.of(DataPermissionAction.valueOf(policy.getActionName()));
            eventPublisher.publishPolicyChanged(resourceKey, policy.getId(), actions);
            log.info("发布策略变更事件: resourceKey={}, policyId={}", resourceKey, policy.getId());
        }
    }

    private String getResourceKey(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        DataResourceEntity resource = resourceRepository.findById(resourceId).orElse(null);
        return resource != null ? resource.getResourceKey() : null;
    }
}