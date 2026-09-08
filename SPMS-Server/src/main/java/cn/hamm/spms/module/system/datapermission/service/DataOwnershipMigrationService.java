package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

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
 * 所以这里直接用 {@link EntityManager} 按主键加载 + 改归属 + merge，不经过业务钩子。</p>
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

    @PersistenceContext
    private EntityManager entityManager;

    private final EntityManagerFactory entityManagerFactory;
    private final SystemPermissionService systemPermissionService;
    private final UserService userService;
    private final DataResourceRepository resourceRepository;
    private final DataPermissionDenyLogRecorder denyLogRecorder;

    public DataOwnershipMigrationService(EntityManagerFactory entityManagerFactory,
                                         SystemPermissionService systemPermissionService,
                                         UserService userService,
                                         DataResourceRepository resourceRepository,
                                         DataPermissionDenyLogRecorder denyLogRecorder) {
        this.entityManagerFactory = entityManagerFactory;
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
        Object target = entityManager.find(entityClass, request.entityId());
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
        owned.setTenantId(request.tenantId());
        owned.setCreatorId(request.creatorId());
        entityManager.merge(owned);

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
        Map<String, Class<?>> byTable = EntityShapeIndex.byTable(EntityShapeIndex.entityClasses(entityManagerFactory));
        Class<?> entityClass = byTable.get(EntityShapeIndex.normalizeTableName(resource.getTableName()));
        if (entityClass == null) {
            PARAM_INVALID.show("找不到资源对应实体：" + resourceKey + "（table=" + resource.getTableName() + "）");
        }
        return entityClass;
    }

    /**
     * 测试替身入口（生产由 {@code @PersistenceContext} 注入）
     *
     * @param entityManager 实体管理器
     */
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
