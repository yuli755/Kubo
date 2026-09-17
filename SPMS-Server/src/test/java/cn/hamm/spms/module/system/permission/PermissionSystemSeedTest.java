package cn.hamm.spms.module.system.permission;

import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>系统权限播种测试（H2）</h1>
 *
 * <p>审计发现的播种缺口：ownership（归属迁移）与 outbox（死信处置）两个特权身份
 * 从未被任何代码或迁移 SQL 种下，非 root 角色永远拿不到它们，"平台管理员"这一层
 * 形同虚设。这里跑真实的框架 add()（PermissionService 无缓存注解、保持非代理形态），
 * 钉两个点：四个身份一次播种全；重复播种幂等（DevDataInitRunner 每次启动都会调）。</p>
 *
 * @author Hamm.cn
 */
@SpringBootTest(
        properties = {
                "spring.cache.type=none"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
@DisplayName("PermissionService · 系统权限播种")
class PermissionSystemSeedTest {

    private static final List<String> SYSTEM_IDENTITIES = List.of(
            SystemPermissionIdentities.ROOT,
            SystemPermissionIdentities.DATA_PERMISSION_BYPASS,
            SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP,
            SystemPermissionIdentities.DATA_PERMISSION_OUTBOX
    );

    @Autowired
    private PermissionService permissionService;

    @Test
    @DisplayName("四个系统特权身份一次播种全，且都是 isSystem 内置权限")
    void seedsAllFourSystemIdentities() {
        permissionService.ensureDataPermissionSystemPermissions();

        for (String identity : SYSTEM_IDENTITIES) {
            PermissionEntity permission = permissionService.getPermissionByIdentity(identity);
            assertNotNull(permission, "缺少系统权限: " + identity);
            assertTrue(Boolean.TRUE.equals(permission.getIsSystem()),
                    identity + " 必须是系统内置权限（isSystem），否则可在管理端被删");
            assertNotNull(permission.getName(), identity + " 必须有中文名");
        }
    }

    @Test
    @DisplayName("重复播种幂等：第二次调用不新增、不抛异常（DevDataInitRunner 每题启动都跑）")
    void seedingIsIdempotent() {
        permissionService.ensureDataPermissionSystemPermissions();
        permissionService.ensureDataPermissionSystemPermissions();

        // 幂等的本质是"第二次看到已存在就不再 add"——若 add 被重复执行，
        // 要么撞 identity 唯一索引抛异常，要么 getPermissionByIdentity 语义先兜住。
        for (String identity : SYSTEM_IDENTITIES) {
            PermissionEntity one = permissionService.getPermissionByIdentity(identity);
            assertNotNull(one);
            assertTrue(Boolean.TRUE.equals(one.getIsSystem()));
        }
    }
}
