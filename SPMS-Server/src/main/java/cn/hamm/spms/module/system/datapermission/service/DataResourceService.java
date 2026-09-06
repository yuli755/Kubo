package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <h1>数据资源管理服务</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class DataResourceService extends BaseService<DataResourceEntity, DataResourceRepository> {
    private final DataPermissionEventPublisher eventPublisher;

    /**
     * 走 provider 接口而不是直接注入实现：列级策略的取数路径（含缓存）只该有一份，
     * 自己 new 一个 repository 查询就等于绕过缓存并另起一套"什么算启用策略"的解释。
     */
    private final DataFieldPolicyProvider fieldPolicyProvider;

    public DataResourceService(DataPermissionEventPublisher eventPublisher, DataFieldPolicyProvider fieldPolicyProvider) {
        this.eventPublisher = eventPublisher;
        this.fieldPolicyProvider = fieldPolicyProvider;
    }

    /**
     * 各资源的"导出明文授权"缺口（管理端自查用）
     *
     * <p>为什么挂在这里而不是新开一个公开端点：这条信息是<b>跨角色</b>的策略地图，
     * 普通用户读到它就能推断"哪个角色的哪个字段有明文"。挂在数据资源管理（本就只对该资源
     * 有查询权限的管理员可见）之下，权限面不扩大。</p>
     *
     * @return 资源键 → 缺口清单；只包含有缺口的资源
     */
    public Map<String, List<FieldPolicyGapAnalyzer.Gap>> exportGaps() {
        List<DataResourceEntity> resources = repository.findAll();
        if (resources == null || resources.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<FieldPolicyGapAnalyzer.Gap>> result = new LinkedHashMap<>();
        for (DataResourceEntity resource : resources) {
            String resourceKey = resource.getResourceKey();
            if (resourceKey == null || resourceKey.isBlank()) {
                continue;
            }
            List<DataFieldPolicyEntity> masking = fieldPolicyProvider
                    .getEnabledPoliciesByResourceAndAction(resourceKey, ColumnMaskingService.DEFAULT_ACTION);
            List<DataFieldPolicyEntity> grants = fieldPolicyProvider
                    .getEnabledPoliciesByResourceAndAction(resourceKey, ExportBypassGuard.ACTION_EXPORT);
            List<FieldPolicyGapAnalyzer.Gap> gaps = FieldPolicyGapAnalyzer.gaps(masking, grants);
            if (!gaps.isEmpty()) {
                result.put(resourceKey, gaps);
            }
        }
        return result;
    }

    @Override
    protected void afterAppAdd(long id, DataResourceEntity entity) {
        super.afterAppAdd(id, entity);
        eventPublisher.publishResourceChanged(entity.getResourceKey());
        log.info("发布资源创建事件: {}", entity.getResourceKey());
    }

    @Override
    protected void afterAppUpdate(long id, DataResourceEntity entity) {
        super.afterAppUpdate(id, entity);
        eventPublisher.publishResourceChanged(entity.getResourceKey());
        log.info("发布资源更新事件: {}", entity.getResourceKey());
    }

    @Override
    protected void beforeDelete(DataResourceEntity entity) {
        super.beforeDelete(entity);
        // 在删除前发布事件
        if (entity != null) {
            eventPublisher.publishResourceChanged(entity.getResourceKey());
            log.info("发布资源删除事件: {}", entity.getResourceKey());
        }
    }
}