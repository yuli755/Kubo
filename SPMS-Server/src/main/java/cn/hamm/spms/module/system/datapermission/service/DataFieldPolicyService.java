package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>数据字段权限策略管理服务</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
@Profile("!test")
public class DataFieldPolicyService extends BaseService<DataFieldPolicyEntity, DataFieldPolicyRepository> implements DataFieldPolicyProvider {
    private final DataPermissionEventPublisher eventPublisher;
    private final DataResourceRepository resourceRepository;

    public DataFieldPolicyService(DataPermissionEventPublisher eventPublisher, DataResourceRepository resourceRepository) {
        this.eventPublisher = eventPublisher;
        this.resourceRepository = resourceRepository;
    }

    /**
     * 根据资源键、字段名、角色编码获取启用的脱敏策略
     *
     * @param resourceKey 数据资源标识（如 wms.input）
     * @param fieldName   字段名
     * @param roleCode    角色编码
     * @return 策略列表（按优先级降序）
     */
    @Override
    @Cacheable(value = "datapermission:field-policies", key = "#resourceKey + ':' + #fieldName + ':' + #roleCode")
    public List<DataFieldPolicyEntity> getPoliciesByResourceFieldRole(
            String resourceKey, String fieldName, String roleCode) {
        return repository.findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue(
                resourceKey, fieldName, roleCode);
    }

    /**
     * 按资源 / 动作取全部启用策略（列级脱敏热路径）
     *
     * <p>缓存键刻意只由 {@code resourceKey} + {@code actionName} 组成：资源 id 解析、
     * 角色命中、优先级选择里，前两项可以缓存，后两项不行。空列表同样缓存
     * （"这个资源压根没配策略"是最常见的情况），策略新增时由
     * {@code publishFieldPolicyEvent} → {@code clearAllCache()} 失效，不会读到脏数据。</p>
     */
    @Override
    @Cacheable(value = "datapermission:field-policies", key = "'list:' + #resourceKey + ':' + #actionName")
    public List<DataFieldPolicyEntity> getEnabledPoliciesByResourceAndAction(String resourceKey, String actionName) {
        DataResourceEntity resource = resourceRepository.getByResourceKey(resourceKey);
        if (resource == null || resource.getId() == null) {
            return java.util.Collections.emptyList();
        }
        List<DataFieldPolicyEntity> policies = repository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(
                resource.getId(), actionName);
        return policies != null ? policies : java.util.Collections.emptyList();
    }

    @Override
    protected void afterAppAdd(long id, DataFieldPolicyEntity entity) {
        super.afterAppAdd(id, entity);
        publishFieldPolicyEvent(entity);
    }

    @Override
    protected void afterAppUpdate(long id, DataFieldPolicyEntity entity) {
        super.afterAppUpdate(id, entity);
        publishFieldPolicyEvent(entity);
    }

    @Override
    protected void beforeDelete(DataFieldPolicyEntity entity) {
        super.beforeDelete(entity);
        // 在删除前发布事件
        publishFieldPolicyEvent(entity);
    }

    private void publishFieldPolicyEvent(DataFieldPolicyEntity policy) {
        String resourceKey = getResourceKey(policy.getResourceId());
        if (resourceKey != null) {
            eventPublisher.publishFieldPolicyChanged(resourceKey);
            log.info("发布字段策略变更事件: resourceKey={}", resourceKey);
        }
    }

    private String getResourceKey(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        DataResourceEntity resource = resourceRepository.findById(resourceId).orElse(null);
        return resource != null ? resource.getResourceKey() : null;
    }

    /**
     * 清理指定资源的字段策略缓存
     */
    @Override
    @CacheEvict(value = "datapermission:field-policies", allEntries = true)
    public void clearCache(String resourceKey) {
        log.debug("清理字段脱敏策略缓存: {}", resourceKey);
    }

    /**
     * 清理所有字段策略缓存
     */
    @Override
    @CacheEvict(value = "datapermission:field-policies", allEntries = true)
    public void clearAllCache() {
        log.debug("清理所有字段脱敏策略缓存");
    }
}