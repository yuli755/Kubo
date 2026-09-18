package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.framework.curd.base.CurdEntity;
import cn.hamm.spms.framework.curd.base.EntityClassIndex;
import cn.hamm.spms.framework.curd.base.EntityRepositories;
import cn.hamm.spms.framework.curd.base.ICurdRepository;
import cn.hamm.spms.framework.curd.model.query.QueryHelper;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;
import cn.hamm.spms.base.permission.AttributionWriteProtection;

/**
 * <h1>归属迁移服务（B1 余半块：谁有权改归属）</h1>
 *
 * <p>默认策略是「归属列一律不可变」（见 {@code AttributionWriteProtection} 与
 * {@code TenantAwareEntity#fillAttribution}）；本服务是那条默认的<b>唯一豁免</b>：
 * 显式持有 {@code system:data-permission:ownership} 权限的角色（通常是平台管理员）可以
 * 通过 {@code POST /dataResource/migrate-ownership} 把一条带归属数据的
 * {@code tenant_id}/{@code creator_id} 迁到目标值。每次迁移都<b>落库审计</b>
 * （{@code data_permission_deny_log}，reason = {@code ownership-migrated}），
 * 因此"谁在什么时候把哪条数据改给了谁"事后可查。</p>
 *
 * <p>为什么不走业务 Service 的通用 update：那条路径会先被
 * {@code BaseService#protectAttribution} 把归属恢复回库中值（防篡改），迁移永远不生效。
 * 所以这里直接按主键读行 + 只写归属两列，不经过业务钩子。</p>
 *
 * <p>J3：原来的实现是 {@code EntityManager.find} + 改字段 + {@code merge}。实体注解退场后，
 * 换成"用写侧仓储按 ID 读一行、再用只含归属列的 {@code UpdateWrapper} 更新" ——
 * 语义等价（只动 {@code tenant_id}/{@code creator_id}），但依赖的是 MyBatis-Plus 而不是 Hibernate。</p>
 *
 * <p>权限校验刻意<b>不借用</b> {@code bypassDataPermission}：bypass 是"数据权限不参与"的
 * 运维旁路，而"改归属"是更窄、更需要留痕的特权，单独用
 * {@code DATA_PERMISSION_OWNERSHIP} 这个身份；root 视为持有该权限。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class DataOwnershipMigrationService {

    /**
     * 归属迁移请求体
     *
     * @param resourceKey 数据资源标识（用于反查实体与审计归因）
     * @param entityId    目标记录主键
     * @param tenantId    目标租户 ID（null 表示不迁移租户）
     * @param creatorId   目标创建人 ID（null 表示不迁移创建人）
     */
    public record OwnershipMigrateRequest(String resourceKey, Long entityId, Long tenantId, Long creatorId) {
    }

    private final EntityRepositories entityRepositories;
    private final EntityClassIndex entityClassIndex;
    private final SystemPermissionService systemPermissionService;
    private final UserService userService;
    private final DataResourceRepository resourceRepository;
    private final DataPermissionDenyLogRecorder denyLogRecorder;

    public DataOwnershipMigrationService(EntityRepositories entityRepositories,
                                         EntityClassIndex entityClassIndex,
                                         SystemPermissionService systemPermissionService,
                                         UserService userService,
                                         DataResourceRepository resourceRepository,
                                         DataPermissionDenyLogRecorder denyLogRecorder) {
        this.entityRepositories = entityRepositories;
        this.entityClassIndex = entityClassIndex;
        this.systemPermissionService = systemPermissionService;
        this.userService = userService;
        this.resourceRepository = resourceRepository;
        this.denyLogRecorder = denyLogRecorder;
    }

    /**
     * 执行归属迁移（事务内：加载 → 改归属 → merge → 落审计）
     *
     * @param request 迁移请求
     */
    @Transactional
    public void migrate(OwnershipMigrateRequest request) {
        if (request == null || request.resourceKey() == null || request.entityId() == null) {
            PARAM_INVALID.show("归属迁移需要 resourceKey 与 entityId");
            return;
        }
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            DataPermissionDenial.denied(null, request.resourceKey(), DataPermissionAudit.REASON_OWNERSHIP_DENIED,
                    "无请求上下文", "数据权限不足：归属迁移需要在请求上下文中执行（需 data-permission:ownership）");
            return;
        }

        UserEntity current = userService.get(context.userId());
        if (current == null || (!current.isRootUser()
                && !systemPermissionService.has(current, SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP))) {
            DataPermissionDenial.denied(null, request.resourceKey(), DataPermissionAudit.REASON_OWNERSHIP_DENIED,
                    "operator=" + context.userId(),
                    "数据权限不足：归属迁移需要 data-permission:ownership 权限（仅平台管理员可执行）");
            return;
        }

        Class<?> entityClass = entityClassOf(request.resourceKey());
        EntityRepositories.Binding binding = entityRepositories.bindingOf(entityClass);
        if (binding == null) {
            PARAM_INVALID.show("该资源没有对应的写侧仓储，无法迁移归属：" + request.resourceKey());
            return;
        }
        CurdEntity<?> target = binding.repository().selectById(request.entityId());
        if (target == null) {
            PARAM_INVALID.show("记录不存在：" + request.resourceKey() + "#" + request.entityId());
            return;
        }
        if (!(target instanceof TenantAwareEntity<?> owned)) {
            PARAM_INVALID.show("该资源不是带归属（tenant_id/creator_id）的实体，无法迁移归属：" + request.resourceKey());
            return;
        }

        Long beforeTenant = owned.getTenantId();
        Long beforeCreator = owned.getCreatorId();
        // 只写归属两列：null 表示"这一列不动"（请求体允许只迁租户或只迁创建人）
        UpdateWrapper<CurdEntity<?>> wrapper = new UpdateWrapper<>();
        wrapper.eq(QueryHelper.column(CurdEntity.class, CurdEntity.STRING_ID), request.entityId());
        if (Objects.nonNull(request.tenantId())) {
            wrapper.set(QueryHelper.column(TenantAwareEntity.class, "tenantId"), request.tenantId());
        }
        if (Objects.nonNull(request.creatorId())) {
            wrapper.set(QueryHelper.column(TenantAwareEntity.class, "creatorId"), request.creatorId());
        }
        migrateColumns(binding, wrapper);

        String detail = "operator=" + context.userId() + " entityId=" + request.entityId()
                + " tenantId " + beforeTenant + "→" + request.tenantId()
                + " creatorId " + beforeCreator + "→" + request.creatorId();
        denyLogRecorder.record(request.resourceKey(), DataPermissionAudit.REASON_OWNERSHIP_MIGRATED, detail);
        log.warn("[data-permission][ownership] 归属迁移：{}", detail);
    }

    private Class<?> entityClassOf(String resourceKey) {
        DataResourceEntity resource = resourceRepository.getByResourceKey(resourceKey);
        if (resource == null) {
            PARAM_INVALID.show("数据资源未登记：" + resourceKey);
        }
        Class<?> entityClass = entityClassIndex.byTableName(resource.getTableName());
        if (entityClass == null) {
            PARAM_INVALID.show("找不到资源对应实体：" + resourceKey + "（table=" + resource.getTableName() + "）");
        }
        return entityClass;
    }

    /**
     * 只改归属列（不碰业务字段，也不触发业务钩子）
     *
     * @param binding 目标实体的仓储绑定
     * @param wrapper 已 set 归属列的更新条件
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void migrateColumns(EntityRepositories.Binding binding, UpdateWrapper<CurdEntity<?>> wrapper) {
        // ICurdRepository<T> 的 T 在这里是"运行期才知道的实体类型"：泛型在解析处已经核对过（泛型参数 == 实体类），
        // 因此这一次原始类型调用是安全的；写成 Class<?> 的分支只会把同一件事抄 41 遍。
        ((ICurdRepository) binding.repository()).update(null, wrapper);
    }
}
