package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
     * 一条导出缺口（带"它属于哪个资源"）
     *
     * <p>为什么把资源标识摊平进每条缺口而不是返回 {@code Map<resourceKey, List<Gap>>}：
     * 这份数据有两个消费方 —— 资源列表页按资源分组汇总，策略编辑器按
     * {@code (resourceId, fieldName)} 定位当前这条策略是否处在缺口里。
     * 编辑器手上只有 {@code resourceId}（表单就提交这个字段），返回 Map 就等于逼前端
     * 再去查一次"id 对应哪个 key"：多一次请求、多一处会写歪的映射。</p>
     *
     * @param resourceId  数据资源 ID（与表单里的 resourceId 同域，可直接比对）
     * @param resourceKey 数据资源标识（人看的）
     * @param fieldName   被遮蔽却缺导出明文授权的字段
     * @param roleCode    生效角色；{@code default} 表示对所有角色兜底
     * @param reason      人话说明（由后端给，前端不重算语义）
     */
    /**
     * 一条"没人读"的列级策略（带资源定位）
     *
     * @param policyId   策略行主键
     * @param resourceId 数据资源 ID
     * @param resourceKey 数据资源标识
     * @param fieldName  字段名
     * @param roleCode   角色编码
     * @param actionName 动作名（原样回显，可能正是拼错的那个值）
     */
    public record InertPolicyItem(Long policyId,
                                  Long resourceId,
                                  String resourceKey,
                                  String fieldName,
                                  String roleCode,
                                  String actionName) {
    }

    /**
     * 列级策略表里"动作不会被任何代码消费"的存量行
     *
     * <p>只读、只报，不提供"顺手停用"：存量行是历史决定的产物，批量改动需要有人先确认
     * 这些行的来源（导入脚本？早期灰度？），那是数据治理决策而不是这个面板的职责。</p>
     *
     * @return 条目列表；无存量问题时为空列表
     */
    public List<InertPolicyItem> inertPolicies() {
        // 消费集取自后端常量本身，而不是又抄一份名单 —— 前端那份由 maskContract 对账
        Set<String> consumed = Set.of(ColumnMaskingService.DEFAULT_ACTION, ExportBypassGuard.ACTION_EXPORT);
        List<DataResourceEntity> resources = repository.findAll();
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        List<InertPolicyItem> items = new ArrayList<>();
        for (DataResourceEntity resource : resources) {
            String resourceKey = resource.getResourceKey();
            if (resourceKey == null || resourceKey.isBlank()) {
                continue;
            }
            for (FieldPolicyGapAnalyzer.Inert inert : FieldPolicyGapAnalyzer.inertPolicies(
                    fieldPolicyProvider.getEnabledPoliciesByResource(resourceKey), consumed)) {
                items.add(new InertPolicyItem(inert.id(), resource.getId(), resourceKey,
                        inert.fieldName(), inert.roleCode(), inert.actionName()));
            }
        }
        return items;
    }

    public record ExportGapItem(Long resourceId,
                                String resourceKey,
                                String fieldName,
                                String roleCode,
                                String reason) {
    }

    /**
     * 各资源的"导出明文授权"缺口（管理端自查用）
     *
     * <p>为什么挂在这里而不是新开一个公开端点：这条信息是<b>跨角色</b>的策略地图，
     * 普通用户读到它就能推断"哪个角色的哪个字段有明文"。挂在数据资源管理（本就只对该资源
     * 有查询权限的管理员可见）之下，权限面不扩大。</p>
     *
     * @return 缺口条目列表（只含有缺口的资源，按资源登记顺序）
     */
    public List<ExportGapItem> exportGaps() {
        List<DataResourceEntity> resources = repository.findAll();
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        List<ExportGapItem> items = new ArrayList<>();
        for (DataResourceEntity resource : resources) {
            String resourceKey = resource.getResourceKey();
            if (resourceKey == null || resourceKey.isBlank()) {
                continue;
            }
            List<DataFieldPolicyEntity> masking = fieldPolicyProvider
                    .getEnabledPoliciesByResourceAndAction(resourceKey, ColumnMaskingService.DEFAULT_ACTION);
            List<DataFieldPolicyEntity> grants = fieldPolicyProvider
                    .getEnabledPoliciesByResourceAndAction(resourceKey, ExportBypassGuard.ACTION_EXPORT);
            for (FieldPolicyGapAnalyzer.Gap gap : FieldPolicyGapAnalyzer.gaps(masking, grants)) {
                items.add(new ExportGapItem(resource.getId(), resourceKey, gap.fieldName(), gap.roleCode(), gap.reason()));
            }
        }
        return items;
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