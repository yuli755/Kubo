package cn.hamm.spms.module.system.permission;

import cn.hamm.spms.module.personnel.role.RoleEntity;
import cn.hamm.spms.module.personnel.role.RoleRepository;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>权限目录同步测试（H2）</h1>
 *
 * <p>钉住 K 报告 §4 P0-1 / P0-2 的修复。此前的缺陷是：权限目录的唯一写入点
 * {@code loadPermission()} 被 {@code DevDataInitRunner} 的两道闸（{@code isDevMode}
 * 与 {@code init.lock}）挡在后面，于是生产环境永不同步 —— 而
 * {@code RequestInterceptor.checkUserPermission} 从库里查权限、且在 SYSTEM_ROOT
 * 放行之前就 fail-closed，任何新增端点对所有人（含 root）都是 403「权限不存在」；
 * 同时该方法只 upsert 从不删除，目录永不收敛（V12 一次性清掉 152 条死码的根因）。</p>
 *
 * <p>这里覆盖四件事：同步幂等且收敛、过期识别只针对系统权限、开启清理后
 * **角色关联与权限行同事务一并删除**、以及过期比例过半时的熔断。
 * 最后一条源码契约测试钉住调用位置 —— 这是最容易被无意改回去的地方，
 * 一旦挪回闸门之后，生产会再次静默失效且没有任何测试会红。</p>
 *
 * @author Hamm.cn
 */
@SpringBootTest(
        properties = {
                "spring.cache.type=none",
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
@DisplayName("PermissionService · 权限目录同步")
class PermissionCatalogSyncTest {

    /** 与 PermissionService.PURGE 熔断阈值对应：过期数 * 2 > 总数 即拒绝清理 */
    private static final int RATIO_GUARD_SEED = 400;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 直接经仓库落一行权限。
     *
     * <p>不走 {@code service.add()}：那会触发框架的校验与编码生成，
     * 而本测试要的是"库里存在一条代码扫描不到的权限"这个前置状态本身。</p>
     */
    private PermissionEntity seedPermission(String identity, boolean isSystem) {
        PermissionEntity entity = new PermissionEntity()
                .setIdentity(identity)
                .setName("测试权限 " + identity)
                .setIsSystem(isSystem)
                .setParentId(0L);
        PermissionEntity saved = permissionRepository.saveAndFlush(entity);
        entityManager.clear();
        return saved;
    }

    @Test
    @DisplayName("同步后目录与代码扫描一致，重复调用幂等且不再报过期")
    void syncIsIdempotentAndConverges() {
        PermissionService.CatalogSyncResult first = permissionService.syncPermissionCatalog();
        assertTrue(first.scanned() > 0, "扫描结果不应为空，否则说明类路径扫描失败");
        assertEquals(0, first.stale(), "首次同步后不应存在过期系统权限");
        assertTrue(first.total() >= first.scanned(),
                "库内总数应不少于扫描数（还有 ensureDataPermissionSystemPermissions 补的四个身份）");

        PermissionService.CatalogSyncResult second = permissionService.syncPermissionCatalog();
        assertEquals(first.total(), second.total(), "第二次同步不应改变权限总数（幂等）");
        assertEquals(0, second.stale(), "第二次同步必须报告目录已收敛");
        assertEquals(0, second.purged(), "默认不开启清理，purged 恒为 0");
    }

    @Test
    @DisplayName("代码里已不存在的系统权限被识别为过期，但默认只报告不删除")
    void detectsStaleSystemPermissionWithoutDeleting() {
        permissionService.syncPermissionCatalog();
        String retired = "RetiredController_getPage";
        seedPermission(retired, true);

        PermissionService.CatalogSyncResult result = permissionService.syncPermissionCatalog();

        assertEquals(1, result.stale(), "应恰好识别出 1 项过期系统权限");
        assertEquals(0, result.purged(), "未开启 kubo.permission.purge-stale 时不得物理删除");
        assertNotNull(permissionService.getPermissionByIdentity(retired),
                "默认只报告不删除，过期权限应仍在库里");
    }

    @Test
    @DisplayName("治理特权身份不得被判为过期（它们没有对应端点，扫描器永远扫不出来）")
    void neverTreatsGovernanceIdentitiesAsStale() {
        permissionService.syncPermissionCatalog();
        PermissionService.CatalogSyncResult result = permissionService.syncPermissionCatalog();

        // 这条断言钉住一个真实踩过的坑：ROOT / BYPASS / OWNERSHIP / OUTBOX 四个身份
        // 由 ensureDataPermissionSystemPermissions() 播种，而 PermissionScanner 遍历的是
        // Controller 的 HTTP 端点，这四个没有端点，故永远不在扫描结果里。
        // 差集若不把它们并入「应当存在」的全集，它们每次都会落进 stale（表现为恒定 +4），
        // 开启 purge-stale 后启动即删，非 root 角色从此拿不到绕过/归属迁移/死信管理三项特权，
        // 而且因为是启动期删的，界面上看不出任何操作痕迹。
        assertEquals(0, result.stale(), "并入治理身份后不应有任何过期项");
        for (String identity : SystemPermissionIdentities.ALL) {
            assertNotNull(permissionService.getPermissionByIdentity(identity),
                    "治理特权身份必须存在: " + identity);
        }
    }

    @Test
    @DisplayName("人工建的非系统权限永不被判为过期（扫描只管代码，管不到人工授权项）")
    void neverTreatsCustomPermissionAsStale() {
        permissionService.syncPermissionCatalog();
        String custom = "CustomGrant_exportAll";
        seedPermission(custom, false);

        PermissionService.CatalogSyncResult result = permissionService.syncPermissionCatalog();

        assertEquals(0, result.stale(), "非系统权限不属于代码扫描的管辖范围，不得计入过期");
        assertNotNull(permissionService.getPermissionByIdentity(custom), "人工权限必须原样保留");
    }

    @Test
    @DisplayName("开启清理后：过期系统权限被物理删除，且角色关联在同一事务内一并解除")
    void purgeRemovesStalePermissionAndDetachesRoles() {
        permissionService.syncPermissionCatalog();

        String retired = "RetiredController_delete";
        PermissionEntity stale = seedPermission(retired, true);
        RoleEntity role = roleRepository.saveAndFlush(new RoleEntity()
                .setName("同步测试角色")
                .setCode("SYNC_TEST_ROLE")
                .setPermissionList(Set.of(stale)));
        entityManager.clear();
        assertNotNull(permissionService.getPermissionByIdentity(retired), "前置：过期权限应已存在");

        ReflectionTestUtils.setField(permissionService, "purgeStalePermission", true);
        try {
            PermissionService.CatalogSyncResult result = permissionService.syncPermissionCatalog();
            assertEquals(1, result.purged(), "应恰好清理 1 项过期权限");
            entityManager.clear();

            // 权限行必须真的没了：role_permission_list.permission_list_id 上有外键，
            // 若"解除关联"与"删除权限"没有同事务、同顺序执行，这里会因约束冲突而失败
            assertNull(permissionService.getPermissionByIdentity(retired), "过期权限应已被物理删除");

            RoleEntity reloaded = roleRepository.findById(role.getId()).orElseThrow();
            assertTrue(reloaded.getPermissionList() == null || reloaded.getPermissionList().isEmpty(),
                    "角色与过期权限的关联必须一并解除，否则残留死引用");
        } finally {
            ReflectionTestUtils.setField(permissionService, "purgeStalePermission", false);
        }
    }

    @Test
    @DisplayName("过期数超过库内一半时熔断拒绝清理（几乎必是扫描侧故障，删除不可逆）")
    void refusesPurgeWhenStaleRatioExceedsHalf() {
        permissionService.syncPermissionCatalog();
        int baseline = permissionService.syncPermissionCatalog().total();

        List<PermissionEntity> fakes = new ArrayList<>();
        for (int i = 0; i < RATIO_GUARD_SEED; i++) {
            fakes.add(new PermissionEntity()
                    .setIdentity("GhostController_method" + i)
                    .setName("幽灵权限 " + i)
                    .setIsSystem(true)
                    .setParentId(0L));
        }
        permissionRepository.saveAllAndFlush(fakes);
        entityManager.clear();
        assertTrue(RATIO_GUARD_SEED * 2 > baseline + RATIO_GUARD_SEED,
                "前置条件不成立：种子数量必须能让过期比例过半，否则本测试测不到熔断分支");

        ReflectionTestUtils.setField(permissionService, "purgeStalePermission", true);
        try {
            PermissionService.CatalogSyncResult result = permissionService.syncPermissionCatalog();
            assertEquals(RATIO_GUARD_SEED, result.stale(), "应识别出全部幽灵权限为过期");
            assertEquals(0, result.purged(), "过期比例过半必须熔断，一项都不许删");
            entityManager.clear();
            assertNotNull(permissionService.getPermissionByIdentity("GhostController_method0"),
                    "熔断后幽灵权限必须原样保留，等待人工核对");
        } finally {
            ReflectionTestUtils.setField(permissionService, "purgeStalePermission", false);
        }
    }

    @Test
    @DisplayName("源码契约：同步调用必须在 isDevMode 与 init.lock 两道闸之前，且不再单独调 loadPermission")
    void syncRunsBeforeDevModeAndLockGates() throws IOException {
        String source = readRunnerSource();
        int syncAt = source.indexOf("permissionService.syncPermissionCatalog()");
        int devModeGateAt = source.indexOf("!appConfig.getIsDevMode()");
        int lockGateAt = source.indexOf("new File(LOCK_FILE).exists()");

        assertTrue(syncAt > 0, "DevDataInitRunner 必须调用 syncPermissionCatalog()");
        assertTrue(devModeGateAt > 0 && lockGateAt > 0, "两道闸的判断应仍存在（本测试依赖它们定位）");
        assertTrue(syncAt < devModeGateAt,
                "同步必须在 isDevMode 闸之前：否则生产环境（isDevMode=false）永不同步权限目录，"
                        + "新增端点对所有人（含 root）都是 403「权限不存在」");
        assertTrue(syncAt < lockGateAt,
                "同步必须在 init.lock 闸之前：否则本地一旦生成 init.lock，后加的接口就永远扫不进来");

        assertFalse(source.contains("permissionService.loadPermission()"),
                "loadPermission() 已并入 syncPermissionCatalog()，不得再单独调用，避免两处各调一半导致口径漂移");
        assertFalse(source.contains("permissionService.ensureDataPermissionSystemPermissions()"),
                "ensureDataPermissionSystemPermissions() 同样已并入同步入口");
    }

    @Test
    @DisplayName("源码契约：清理必须\"先解除角色关联再删权限行\"，顺序不可颠倒")
    void purgeDetachesBeforeDeleting() throws IOException {
        String source = readSource("src/main/java/cn/hamm/spms/module/system/permission/PermissionService.java");
        int detachAt = source.indexOf("detachRolesByPermissionIds(");
        int deleteAt = source.indexOf("deleteAllByIdInBatch(");

        assertTrue(detachAt > 0, "必须调用 detachRolesByPermissionIds 解除角色关联");
        assertTrue(deleteAt > 0, "必须调用 deleteAllByIdInBatch 删除权限行");
        assertTrue(detachAt < deleteAt,
                "role_permission_list.permission_list_id 上有外键：先删权限行会让整批删除以约束冲突失败");

        assertTrue(source.contains("public CatalogSyncResult syncPermissionCatalog()"),
                "同步入口方法签名应存在");
        // 关键契约：给本服务标注 @Transactional 会让 PermissionService 整个变成 CGLIB
        // 事务代理，破坏 BaseService.repository 的字段注入（曾致 /permission/getList 因
        // repository 为 null 全线 500）。因此同步入口**必须不标** @Transactional，
        // 原子性改由 TransactionHelper.run 提供。
        assertFalse(source.matches("(?s).*@Transactional\\s+public CatalogSyncResult syncPermissionCatalog\\(\\)"),
                "syncPermissionCatalog 不得标 @Transactional：它会破坏 BaseService.repository 字段注入");
        assertTrue(source.contains("transactionHelper.run("),
                "purge 的解关联+删除必须经 TransactionHelper.run 保证同事务原子性");
    }

    /**
     * 读源码做契约断言。
     *
     * <p>Maven 下测试的工作目录是模块根（SPMS-Server），但从仓库根跑 IDE 时是仓库根，
     * 故两个候选路径都试一次，避免因运行方式不同而误报。</p>
     */
    private String readSource(String relative) throws IOException {
        for (Path candidate : List.of(Path.of(relative), Path.of("SPMS-Server", relative))) {
            if (Files.exists(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("找不到源文件: " + relative);
    }

    private String readRunnerSource() throws IOException {
        return readSource("src/main/java/cn/hamm/spms/DevDataInitRunner.java");
    }
}
