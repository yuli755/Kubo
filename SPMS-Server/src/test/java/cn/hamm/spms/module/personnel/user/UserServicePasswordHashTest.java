package cn.hamm.spms.module.personnel.user;

import cn.hamm.spms.framework.curd.permission.PermissionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>用户密码加盐散列回归测试（审核项 A-1 / A-4）</h1>
 *
 * <p>历史上 /user/add、/user/update 走通用 Curd 落库时把请求体里的 password 原样写库
 * （明文 + 空盐）：凭据裸奔不说，登录比对因「盐为空」直接抛错，新建账号根本无法登录。
 * A-4 是它的镜像事故：管理员轮换密码时，框架合并（null 忽略）会把库里的旧盐带进落库
 * 实体，"盐为空"判据在落库点失明 → 裸密码带着旧盐入库，新密码永远登不上。修复分三道
 * 闸门：beforeAdd / beforeUpdate（仅提交值阶段归一）+ beforeAppSaveToDatabase（兜底）。
 * 本测试钉住六道契约：</p>
 * <ul>
 *   <li>裸密码入库前必被加盐散列，且与登录侧 encodePassword 可互验；</li>
 *   <li>轮换（裸密码 + 库里旧盐）必须先散列再合并，新密码可登录；</li>
 *   <li>内部流程成对提交的「盐 + 40 位散列」原样透传，不二次散列；</li>
 *   <li>客户端自带盐的裸密码：盐作废，重新加盐散列；</li>
 *   <li>不含密码字段的普通更新不误伤；</li>
 *   <li>裸密码恰好长得像散列（40 位十六进制）但没配服务端盐 → 仍按裸密码处理。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@DisplayName("UserService · 密码加盐散列三道闸门")
class UserServicePasswordHashTest {

    private final UserService service = new UserService();

    @Test
    @DisplayName("裸密码落库 → 加盐散列，登录侧可互验")
    void hashesRawPasswordOnSave() {
        UserEntity user = new UserEntity();
        user.setEmail("audit-hash@kubo.test");
        user.setPassword("Zz#Kubo2026");

        service.beforeAppSaveToDatabase(user);

        assertNotEquals("Zz#Kubo2026", user.getPassword(), "明文凭据绝不允许落库");
        assertEquals(UserService.PASSWORD_SALT_LENGTH, user.getSalt().length(),
                "盐长必须与 PASSWORD_SALT_LENGTH 约定一致");
        // 登录比对口径：encodePassword(输入明文, 库中盐) == 库中散列
        assertEquals(PermissionUtil.encodePassword("Zz#Kubo2026", user.getSalt()), user.getPassword(),
                "散列必须与登录侧同一算法，否则新账号登录必挂（A-1 的功能面后果）");
    }

    @Test
    @DisplayName("A-4 轮换：提交实体带库里旧盐 + 裸密码 → 归一后旧盐作废、可登录互验")
    void rotationHashesBeforeMerge() {
        UserEntity submitted = new UserEntity();
        submitted.setId(9L);
        submitted.setPassword("Rotated#2026");
        // 模拟框架合并语义（null 忽略）：提交实体可能已带库中旧盐；
        submitted.setSalt("oldS");
        // beforeUpdate 全链要 Spring 协作者（repository 回表），这里单测归一缝本身
        invokeNormalize(submitted);

        assertNotEquals("Rotated#2026", submitted.getPassword());
        assertNotEquals("oldS", submitted.getSalt(), "客户端/旧盐必须作废，换服务端新盐");
        assertEquals(PermissionUtil.encodePassword("Rotated#2026", submitted.getSalt()),
                submitted.getPassword(), "轮换后的凭据必须与登录侧互验");
    }

    @Test
    @DisplayName("内部成对凭据（resetPassword/register/DevDataInit 形状）→ 原样透传不二次散列")
    void preHashedPairUntouched() {
        String salt = "ab12";
        String hashed = PermissionUtil.encodePassword("Old#Pass1", salt);
        UserEntity user = new UserEntity();
        user.setPassword(hashed);
        user.setSalt(salt);

        service.beforeAppSaveToDatabase(user);

        assertEquals(hashed, user.getPassword(), "已散列凭据再散列一次 = 专用改密流程全员登录挂");
        assertEquals(salt, user.getSalt());
    }

    @Test
    @DisplayName("裸密码 + 客户端自带盐 → 盐作废，服务端重新加盐散列")
    void clientSuppliedSaltRejected() {
        UserEntity user = new UserEntity();
        user.setPassword("Zz#Kubo2026");
        user.setSalt("evil");

        invokeNormalize(user);

        assertEquals(UserService.PASSWORD_SALT_LENGTH, user.getSalt().length());
        assertEquals(PermissionUtil.encodePassword("Zz#Kubo2026", user.getSalt()), user.getPassword());
    }

    /** 归一缝是 private：全链 beforeUpdate/beforeAdd 需要 Spring 协作者，单测直连归一方法 */
    private void invokeNormalize(UserEntity entity) {
        try {
            java.lang.reflect.Method m = UserService.class
                    .getDeclaredMethod("normalizeSubmittedPassword", UserEntity.class);
            m.setAccessible(true);
            m.invoke(service, entity);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("modifyPassword 形状（盐长不定长的历史形状）+ 散列成对 → 透传不二次散列")
    void modifyPasswordShapeUntouched() {
        // 历史上 modifyPassword 用 RandomUtil.randomString() 生成过非定长盐；
        // 凭据对判据若按"盐定长"一刀切，这类成对凭据会被兜底网二次散列 → 用户改密后永远登不上（S13b 实测事故）
        String salt = "aLongLegacyShapedSalt32chars__";
        UserEntity user = new UserEntity();
        user.setSalt(salt);
        user.setPassword(PermissionUtil.encodePassword("NewP@ss2026", salt));

        service.beforeAppSaveToDatabase(user);

        assertEquals(salt, user.getSalt());
        assertEquals(PermissionUtil.encodePassword("NewP@ss2026", salt), user.getPassword());
    }

    @Test
    @DisplayName("普通资料更新（不带 password 字段）→ 不产生盐、不动密码")
    void passwordlessUpdateUnaffected() {
        UserEntity user = new UserEntity();
        user.setNickname("改名不碰密码");

        service.beforeAppSaveToDatabase(user);

        assertNull(user.getPassword());
        assertNull(user.getSalt());
    }

    @Test
    @DisplayName("裸密码恰好是 40 位十六进制（无服务端盐配对）→ 仍按裸密码散列")
    void hexLookingRawPasswordStillHashed() {
        String rawHex = "a".repeat(40);
        UserEntity user = new UserEntity();
        user.setPassword(rawHex);

        service.beforeAppSaveToDatabase(user);

        assertNotEquals(rawHex, user.getPassword(), "形状像散列不等于散列：没有服务端盐配对就必须重散列");
        assertEquals(UserService.PASSWORD_SALT_LENGTH, user.getSalt().length());
    }

    @Test
    @DisplayName("password 为空白 → 不触发加盐，不生成随机盐")
    void blankPasswordNotTouched() {
        UserEntity user = new UserEntity();
        user.setPassword("   ");

        service.beforeAppSaveToDatabase(user);

        assertNull(user.getSalt());
        assertTrue(user.getPassword() == null || user.getPassword().isBlank());
    }
}
