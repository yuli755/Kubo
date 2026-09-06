package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>测试用数据字段权限策略管理服务</h1>
 * <p>测试环境下不使用缓存，直接查询数据库</p>
 *
 * @author Hamm.cn
 */
@Profile("test")
@Service
public class TestDataFieldPolicyService implements DataFieldPolicyProvider {
    private final DataFieldPolicyRepository repository;
    private final DataResourceRepository resourceRepository;
    private final DataPermissionEventPublisher eventPublisher;

    public TestDataFieldPolicyService(DataFieldPolicyRepository repository, DataResourceRepository resourceRepository, DataPermissionEventPublisher eventPublisher) {
        this.repository = repository;
        this.resourceRepository = resourceRepository;
        this.eventPublisher = eventPublisher;
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
    public List<DataFieldPolicyEntity> getPoliciesByResourceFieldRole(
            String resourceKey, String fieldName, String roleCode) {
        return repository.findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue(
                resourceKey, fieldName, roleCode);
    }

    /**
     * 按资源 / 动作取全部启用策略 —— 测试环境无缓存，直接回表
     */
    @Override
    public List<DataFieldPolicyEntity> getEnabledPoliciesByResourceAndAction(String resourceKey, String actionName) {
        DataResourceEntity resource = resourceRepository.getByResourceKey(resourceKey);
        if (resource == null || resource.getId() == null) {
            return List.of();
        }
        List<DataFieldPolicyEntity> policies = repository
                .findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(resource.getId(), actionName);
        return policies != null ? policies : List.of();
    }

    /**
     * 清理指定资源的字段策略缓存 - 测试环境无缓存，空实现
     */
    @Override
    public void clearCache(String resourceKey) {
        // no-op: test profile doesn't use caching
    }

    /**
     * 清理所有字段策略缓存 - 测试环境无缓存，空实现
     */
    @Override
    public void clearAllCache() {
        // no-op: test profile doesn't use caching
    }
}