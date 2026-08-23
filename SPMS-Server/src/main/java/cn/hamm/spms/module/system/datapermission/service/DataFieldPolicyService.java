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