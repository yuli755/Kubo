package cn.hamm.spms.module.system.datapermission.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>{@code owner_columns} 登记语法的用例</h1>
 *
 * <p>这个串同时被读侧（{@code DataPermissionSpecification}）、写侧校验（{@code FieldAllowList}）、
 * 工厂门禁（{@code FactoryDimensionReadiness}）与盘点面板消费。语法判据一旦各自解释，
 * 结果就是"面板说合法、运行期绑不上"或反过来。所以语法本身要有用例，而不只是被顺带用到。</p>
 *
 * <p>特别注意<b>非法输入必须给原因</b>：这个字段在库里是自由文本，配错的代价是"策略看起来生效了
 * 其实没有"，那是最难查的一类问题。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("OwnerColumnRegistration · 归属列登记语法")
class OwnerColumnRegistrationTest {

    @Test
    @DisplayName("空值与裸写法都合法：现网存量登记一律是裸写法，不能被新语法判死")
    void legacyRegistrationsStayValid() {
        assertNull(OwnerColumnRegistration.validate(null));
        assertNull(OwnerColumnRegistration.validate("   "));
        assertNull(OwnerColumnRegistration.validate("creatorId"));
        assertNull(OwnerColumnRegistration.validate("creatorId,departmentId"));
        assertNull(OwnerColumnRegistration.validate("creatorId,,storage.id"));
    }

    @Test
    @DisplayName("维度前缀：大小写都认，解析后统一成大写档位名")
    void dimensionPrefixIsCaseInsensitive() {
        assertNull(OwnerColumnRegistration.validate("user:creatorId"));

        List<OwnerColumnRegistration> items = OwnerColumnRegistration.parse("user:creatorId");
        assertEquals(1, items.size());
        assertEquals("USER", items.get(0).dimension());
        assertEquals("creatorId", items.get(0).path());
        assertEquals(List.of("creatorId"), OwnerColumnRegistration.pathsFor(items, "USER"));
    }

    @Test
    @DisplayName("关联路径：点号写法合法，且段名两种写法都保留原样")
    void associationPathIsAccepted() {
        assertNull(OwnerColumnRegistration.validate("WAREHOUSE:storage.id"));
        assertNull(OwnerColumnRegistration.validate("storage_detail.id"));

        List<OwnerColumnRegistration> items = OwnerColumnRegistration.parse("WAREHOUSE:storage.id");
        assertEquals(List.of("storage.id"), OwnerColumnRegistration.pathsFor(items, "WAREHOUSE"));
    }

    @Test
    @DisplayName("前缀不是维度成员 → 整串判非法，绝不悄悄忽略这一项")
    void unknownDimensionPrefixFailsWholeValue() {
        String reason = OwnerColumnRegistration.validate("WAREHOUSEX:storage.id");

        assertNotNull(reason);
        assertTrue(reason.contains("DataPermissionSubjectType"), reason);
        // 悄悄忽略的结果是"配了没用"，比报错更糟，所以解析也必须把它丢掉而不是当成裸写法收下
        assertTrue(OwnerColumnRegistration.parse("WAREHOUSEX:storage.id").isEmpty());
    }

    @Test
    @DisplayName("超过一跳判非法，并把改法写在原因里")
    void tooDeepPathIsRejectedWithAdvice() {
        String reason = OwnerColumnRegistration.validate("storage.owner.id");

        assertNotNull(reason);
        assertTrue(reason.contains("一跳"), reason);
        assertEquals(2, OwnerColumnRegistration.MAX_SEGMENTS);
    }

    @Test
    @DisplayName("注入形状仍然进不来：点号不等于放开自由文本")
    void sqlShapeIsStillRejected() {
        // SQL-IGNORE: 恶意输入样例（断言校验器把它判为"需要转义/拒绝"）
        assertNotNull(OwnerColumnRegistration.validate("a; DROP TABLE x"));
        assertNotNull(OwnerColumnRegistration.validate("a b"));
        assertNotNull(OwnerColumnRegistration.validate("a--b"));
        assertNotNull(OwnerColumnRegistration.validate("1abc"));
        assertNotNull(OwnerColumnRegistration.validate("a."));
        assertNotNull(OwnerColumnRegistration.validate("WAREHOUSE:"));
    }

    @Test
    @DisplayName("带前缀的项不混进裸写法：部门档与负责人档各拿各的")
    void taggedAndUntaggedAreSeparated() {
        List<OwnerColumnRegistration> items = OwnerColumnRegistration
                .parse("creatorId,DEPARTMENT:dept_id,WAREHOUSE:storage.id");

        assertEquals(List.of("creatorId"), OwnerColumnRegistration.untaggedPaths(items));
        assertEquals(List.of("dept_id"), OwnerColumnRegistration.pathsFor(items, "DEPARTMENT"));
        assertEquals(List.of("storage.id"), OwnerColumnRegistration.pathsFor(items, "WAREHOUSE"));
        assertTrue(OwnerColumnRegistration.pathsFor(items, "FACTORY").isEmpty());
    }
}
