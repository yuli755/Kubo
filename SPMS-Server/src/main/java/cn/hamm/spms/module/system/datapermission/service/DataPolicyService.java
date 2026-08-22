package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

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

    public DataPolicyService(DataPermissionEventPublisher eventPublisher, DataResourceRepository resourceRepository) {
        this.eventPublisher = eventPublisher;
        this.resourceRepository = resourceRepository;
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