package cn.hamm.spms.module.system.permission;

import cn.hamm.spms.Application;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.common.security.PermissionScanner;
import cn.hamm.spms.module.system.permission.enums.PermissionType;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_DELETE;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Service
@Slf4j
public class PermissionService extends BaseService<PermissionEntity, PermissionRepository> {

    public void loadPermission() {
        loadPermission(PermissionScanner.scanPermission(Application.class.getPackageName()));
    }

    /**
     * 把已扫描出的权限树写入目录（**只插入或更新，不删除**）。
     *
     * <p>拆出这个重载是为了让 {@link #syncPermissionCatalog()} 复用同一份扫描结果：
     * 同步流程需要「扫描一次，既用于 upsert，又用于计算过期差集」。
     * 若无参版本内部自己扫，反射扫描就会被执行两遍 —— 该方法要遍历全部 Controller
     * 的全部方法，是启动期并不便宜的开销。</p>
     *
     * @param permissions {@link PermissionScanner#scanPermission(String)} 的结果
     */
    public void loadPermission(List<PermissionEntity> permissions) {
        for (var permission : permissions) {
            PermissionEntity exist = getPermissionByIdentity(permission.getIdentity());
            long existId;
            if (Objects.isNull(exist)) {
                exist = new PermissionEntity()
                        .setName(permission.getName())
                        .setIdentity(permission.getIdentity())
                        .setIsSystem(true);
                existId = add(exist);
            } else {
                existId = exist.getId();
                exist.setName(permission.getName())
                        .setIdentity(permission.getIdentity())
                        .setIsSystem(true);
                updateToDatabase(exist);
            }
            exist = get(existId);
            for (PermissionEntity subPermission : permission.getChildren()) {
                PermissionEntity existSub = getPermissionByIdentity(subPermission.getIdentity());
                if (Objects.isNull(existSub)) {
                    existSub = new PermissionEntity()
                            .setName(subPermission.getName())
                            .setIdentity(subPermission.getIdentity())
                            .setIsSystem(true)
                            .setParentId(exist.getId());
                    add(existSub);
                } else {
                    existSub.setName(subPermission.getName())
                            .setIdentity(subPermission.getIdentity())
                            .setIsSystem(true)
                            .setParentId(exist.getId());
                    updateToDatabase(existSub);
                }
            }
        }
    }
    /**
     * 按权限标识查询权限。
     *
     * <p>补齐 airpower 退场时遗漏的读出口：本类三处与 {@code RequestInterceptor}
     * 均调用它，但方法体在迁移中丢失，导致编译期「找不到符号」。
     * 实现与 main 分支一致，直接委托 {@code PermissionRepository.getByIdentity}。</p>
     *
     * @param identity 权限标识
     * @return 命中的权限；不存在返回 null
     */
    public PermissionEntity getPermissionByIdentity(String identity) {
        return repository.getByIdentity(identity);
    }

    /**
     * 确保系统级权限存在，供权限迁移和开发初始化幂等调用。
     *
     * @param identity 权限标识
     * @param name 权限名称
     * @return 已存在或新建的权限
     */
    public PermissionEntity ensureSystemPermission(String identity, String name) {
        PermissionEntity permission = getPermissionByIdentity(identity);
        if (permission == null) {
            permission = new PermissionEntity()
                    .setIdentity(identity)
                    .setName(name)
                    .setIsSystem(true)
                    .setType(PermissionType.API.getKey());
            add(permission);
        } else if (!Boolean.TRUE.equals(permission.getIsSystem())) {
            permission.setIsSystem(true);
            updateToDatabase(permission);
        }
        return permission;
    }

    /**
     * 确保权限治理所需的系统权限存在。
     *
     * <p>四个身份与 {@code SystemPermissionIdentities} 一一对应：少种任何一个，
     * 对应特权（绕过/归属迁移/死信管理）就无法授予非 root 角色，只能靠代码里的
     * isRootUser 兜底，"平台管理员"这一层形同虚设。与
     * {@code docs/database/migration/20260909_...consolidated.sql} +
     * {@code 20260914_system_permission_identities.sql} 保持同一份口径。</p>
     */
    public void ensureDataPermissionSystemPermissions() {
        ensureSystemPermission(SystemPermissionIdentities.ROOT, "系统超级权限");
        ensureSystemPermission(SystemPermissionIdentities.DATA_PERMISSION_BYPASS, "绕过数据权限");
        ensureSystemPermission(SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP, "迁移数据归属");
        ensureSystemPermission(SystemPermissionIdentities.DATA_PERMISSION_OUTBOX, "权限变更死信管理");
    }

    /**
     * 权限目录同步结果，供启动日志与测试断言使用。
     *
     * @param scanned  本次扫描出的权限标识数（控制器节点 + 方法子节点）
     * @param total    同步后库内的权限总数
     * @param stale    库中存在但已扫描不到的系统权限数
     * @param purged   实际物理删除的过期权限数（未开启清理或被阈值拦下时恒为 0）
     */
    public record CatalogSyncResult(int scanned, int total, int stale, int purged) {
    }

    /** 过期权限写日志时最多列举多少个标识，避免启动日志被几百行刷屏 */
    private static final int STALE_LOG_SAMPLE = 20;

    /**
     * 是否在启动时**物理删除**过期权限，默认 false（只报告不删除）。
     *
     * <p>默认关闭是刻意的：删除会连带解除角色关联，属于不可逆操作，
     * 应当由运维在确认过扫描结果之后、于维护窗口内显式开启。</p>
     */
    @Value("${kubo.permission.purge-stale:false}")
    private boolean purgeStalePermission;

    /**
     * 同步权限目录：扫描代码 → upsert → 计算并处置过期项。
     *
     * <p><b>为什么需要它</b>：此前权限目录的唯一写入点是 {@code DevDataInitRunner} 里的
     * {@code loadPermission()}，而那个 runner 被两道闸挡着 ——
     * {@code appConfig.getIsDevMode()} 与运行目录下的 {@code init.lock}。后果有两条：</p>
     * <ol>
     *   <li><b>生产环境永不同步</b>：{@code isDevMode} 为 false 时直接 return。
     *       而 {@code RequestInterceptor.checkUserPermission} 是**从库里查权限**且
     *       在 SYSTEM_ROOT 放行之前就 fail-closed 的，所以任何新增端点在生产上
     *       对所有人（含 root）都是 403「权限不存在」，只能手工塞数据。</li>
     *   <li><b>目录永不收敛</b>：{@code loadPermission()} 只插入或更新、从不删除，
     *       下线接口的权限行会永久残留（这正是需要 V12 一次性清掉 152 条死码的根因）。</li>
     * </ol>
     *
     * <p>本方法因此与「是否开发者模式」「是否已初始化过」**解耦**，由启动流程无条件调用，
     * 且可重复执行（幂等）。</p>
     *
     * <p><b>事务边界 —— 有意不标 {@code @Transactional}</b>：本服务的方法一旦标注，
     * Spring 会把它整个变成 CGLIB 事务代理，而 {@code BaseService.repository} 是
     * {@code @Autowired(required = false)} 字段注入，代理化会破坏这条注入链 ——
     * 曾实测导致 {@code /permission/getList} 因 {@code repository} 为 null 而全线 500。
     * 「解除角色关联」与「删除权限行」两步必须同事务，故交由
     * {@link cn.hamm.spms.common.transaction.TransactionHelper} 的独立事务模板在
     * purge 处包裹执行，而不是给类/方法加注解。</p>
     *
     * @return 同步结果统计
     */
    public CatalogSyncResult syncPermissionCatalog() {
        List<PermissionEntity> scanned = PermissionScanner.scanPermission(Application.class.getPackageName());
        Set<String> scannedIdentities = new HashSet<>();
        for (PermissionEntity controller : scanned) {
            scannedIdentities.add(controller.getIdentity());
            for (PermissionEntity child : controller.getChildren()) {
                scannedIdentities.add(child.getIdentity());
            }
        }

        // 灾难性清空保护：扫描结果为空几乎一定意味着类路径扫描失败（打包方式变化、
        // 反射被裁剪、包名写错），而不是「所有权限真的都没了」。此时若继续算差集，
        // 整份目录都会被判为过期；一旦运维开了 purge-stale，就会把所有角色的权限删光，
        // 全员 403 且无法再从界面恢复。故宁可放弃本次同步并报错。
        // 注意本判断必须在并入治理身份**之前**：并入之后集合恒非空，保护就失效了。
        if (scannedIdentities.isEmpty()) {
            log.error("权限扫描结果为空，跳过目录同步：这通常表示类路径扫描失败，而不是权限真的全部消失");
            return new CatalogSyncResult(0, 0, 0, 0);
        }

        // 扫描出的端点数，仅用于结果统计（治理特权不是扫描产物，不该计入这个口径）
        int scannedEndpoints = scannedIdentities.size();
        // 把治理特权身份并入「应当存在」的全集。它们没有对应的 HTTP 端点，
        // PermissionScanner 遍历的是 Controller 方法，因此永远扫不出这四个身份。
        // 不并入的后果是它们每次都落进 stale 差集：报告口径失真只是小事，
        // 一旦运维开启 purge-stale，**启动即删掉**绕过数据权限 / 迁移数据归属 /
        // 死信管理三项特权，非 root 角色从此永远拿不到，而且因为是启动期删的，
        // 界面上看不出任何操作痕迹。这是本方法最容易踩的坑，已由
        // PermissionCatalogSyncTest 的三处断言钉住。
        scannedIdentities.addAll(SystemPermissionIdentities.ALL);

        loadPermission(scanned);
        ensureDataPermissionSystemPermissions();

        List<PermissionEntity> all = filter(new PermissionEntity());
        List<PermissionEntity> stale = all.stream()
                // 只认系统权限：人工建的非系统权限不属于代码扫描的管辖范围，删不得
                .filter(permission -> Boolean.TRUE.equals(permission.getIsSystem()))
                .filter(permission -> !scannedIdentities.contains(permission.getIdentity()))
                .toList();

        if (stale.isEmpty()) {
            log.info("权限目录已收敛：扫描 {} 项，库内 {} 项，无过期项", scannedEndpoints, all.size());
            return new CatalogSyncResult(scannedEndpoints, all.size(), 0, 0);
        }

        List<String> staleIdentities = stale.stream().map(PermissionEntity::getIdentity).sorted().toList();
        List<String> sample = staleIdentities.subList(0, Math.min(STALE_LOG_SAMPLE, staleIdentities.size()));
        log.warn("权限目录存在 {} 项过期系统权限（扫描 {} 项 / 库内 {} 项），示例：{}{}",
                stale.size(), scannedEndpoints, all.size(), String.join(", ", sample),
                staleIdentities.size() > sample.size() ? " 等" : "");

        if (!purgeStalePermission) {
            log.warn("未开启 kubo.permission.purge-stale，过期权限只报告不删除。"
                    + "它们不会造成越权（已无对应端点，拦截器按 identity 精确匹配），"
                    + "但会让权限树持续膨胀、角色授权界面出现点不动的死项");
            return new CatalogSyncResult(scannedEndpoints, all.size(), stale.size(), 0);
        }

        // 比例阈值：过期数超过库内一半，几乎一定是扫描侧出了问题（例如只扫到部分包），
        // 而不是真的下线了一半接口。删除不可逆，故拒绝自动执行并要求人工核对。
        if (stale.size() * 2 > all.size()) {
            log.error("过期权限 {} 项已超过库内 {} 项的一半，拒绝自动清理；请人工核对扫描结果后再执行",
                    stale.size(), all.size());
            return new CatalogSyncResult(scannedEndpoints, all.size(), stale.size(), 0);
        }

        List<Long> ids = stale.stream().map(PermissionEntity::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            // 理论上到不了这里（stale 非空且都取自库内），但原生 SQL 的 in () 遇空集合是非法语法，
            // 与其让它在启动期抛一个难懂的 SQL 异常，不如在此显式收口。
            log.error("过期权限 {} 项均无有效主键，跳过清理", stale.size());
            return new CatalogSyncResult(scannedEndpoints, all.size(), stale.size(), 0);
        }
        // 先解除角色关联再删权限行：role_permission_list.permission_list_id 上有外键，
        // 顺序反了整批删除会以约束冲突失败。两步必须同事务，但本服务不能标
        // @Transactional（见 syncPermissionCatalog 注释），故交由 TransactionHelper
        // 的独立事务模板包裹，保证「解除关联」与「删除权限」要么都成、要么都回滚。
        int[] detached = new int[1];
        if (transactionHelper != null) {
            transactionHelper.run(() -> {
                detached[0] = repository.detachRolesByPermissionIds(ids);
                repository.deleteAllByIdInBatch(ids);
            });
        } else {
            detached[0] = repository.detachRolesByPermissionIds(ids);
            repository.deleteAllByIdInBatch(ids);
        }
        log.info("已清理 {} 项过期系统权限，连带解除 {} 条角色关联", ids.size(), detached[0]);
        return new CatalogSyncResult(scannedEndpoints, all.size() - ids.size(), stale.size(), ids.size());
    }
}


