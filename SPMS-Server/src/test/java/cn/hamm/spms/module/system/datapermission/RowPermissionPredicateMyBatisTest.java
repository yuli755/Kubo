package cn.hamm.spms.module.system.datapermission;

import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 片段换写（JPA 命名参数 → MyBatis 命名参数）的契约。
 *
 * <p>这层接缝之所以要有自己的测试：它不产生条件，只改条件的<em>写法</em>，因此一旦出错，
 * 表现不是"报错"而是"两边看到的行不一样"或"某条条件静默失效"。这类失效在集成测试里
 * 只体现为行数差异，归因成本极高，所以在纯单测里先把形状钉死。</p>
 */
@DisplayName("权限片段的 MyBatis 换写契约")
class RowPermissionPredicateMyBatisTest {

    @Test
    @DisplayName("只换写法：值仍然参数化，一个字都不进 SQL 文本")
    void translatesPlaceholdersOnly() {
        RowPermissionPredicate predicate = new RowPermissionPredicate(
                "(material.tenant_id = :dpRule0)", Map.of("dpRule0", 7L));

        RowPermissionPredicate translated = predicate.forMyBatis();

        assertEquals("(material.tenant_id = #{dpRule0})", translated.sql());
        assertEquals(Map.of("dpRule0", 7L), translated.parameters());
        // 值不许内联：这是渲染器 javadoc 里那条"标识符白名单 + 值参数化"的延续
        assertFalse(translated.sql().contains("7"));
    }

    @Test
    @DisplayName("IN 的逐项参数照常换写（渲染器不产集合值，这里也不接受）")
    void translatesExpandedInItems() {
        RowPermissionPredicate predicate = new RowPermissionPredicate(
                "(material.creator_id IN (:dpRule0_0, :dpRule0_1))",
                Map.of("dpRule0_0", 1L, "dpRule0_1", 2L));

        assertEquals("(material.creator_id IN (#{dpRule0_0}, #{dpRule0_1}))",
                predicate.forMyBatis().sql());
    }

    @Test
    @DisplayName("恒假片段原样通过（它没有参数，也不该有）")
    void denyFragmentPassesThrough() {
        RowPermissionPredicate deny = RowPermissionPredicate.deny();

        assertEquals("1 = 0", deny.forMyBatis().sql());
    }

    @Test
    @DisplayName("片段引用了没提供的参数 ⇒ 抛，不发 SQL")
    void rejectsUnknownPlaceholder() {
        RowPermissionPredicate predicate = new RowPermissionPredicate(
                "(material.tenant_id = :nope)", Map.of());

        assertThrows(IllegalArgumentException.class, predicate::forMyBatis);
    }

    @Test
    @DisplayName("参数表里有片段没用到的键 ⇒ 抛（片段被截断的表现就是多放行行）")
    void rejectsUnusedParameter() {
        RowPermissionPredicate predicate = new RowPermissionPredicate(
                "(material.tenant_id = :dpRule0)", Map.of("dpRule0", 7L, "dpRule1", 8L));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, predicate::forMyBatis);
        assertTrue(ex.getMessage().contains("dpRule1"), ex.getMessage());
    }

    @Test
    @DisplayName("集合值 / null 值 / 非白名单类型 ⇒ 抛（出现即说明片段不是渲染器产出的）")
    void rejectsNonScalarAndNullValues() {
        assertThrows(IllegalArgumentException.class, () -> new RowPermissionPredicate(
                "(material.tenant_id IN (:dpRule0))", Map.of("dpRule0", List.of(1L, 2L))).forMyBatis());
        // null 值能活过构造器（Map.copyOf 对单键映射不校验值 —— 这条是 CI 教我的，见 requireBindable 注释），
        // 所以换写阶段必须自己拦下来：绑一个 NULL 进 "col = ?" 在 SQL 里是"未知"，
        // 与恒假等价，但等价要靠方言行为维持；这里宁可抛，不让语义寄存在驱动上
        assertThrows(IllegalArgumentException.class, () -> new RowPermissionPredicate(
                "(material.tenant_id = :dpRule0)", java.util.Collections.singletonMap("dpRule0", null)).forMyBatis());
        assertThrows(IllegalArgumentException.class, () -> new RowPermissionPredicate(
                "(material.created_at > :dpRule0)",
                Map.of("dpRule0", java.time.Instant.parse("2026-09-07T00:00:00Z"))).forMyBatis());
    }

    @Test
    @DisplayName("分号与注释一律拒（片段里出现这两样就不是白名单产物）")
    void rejectsStatementBreakers() {
        assertThrows(IllegalArgumentException.class, () -> new RowPermissionPredicate(
                // SQL-IGNORE: 恶意输入样例（片段注入，断言转义后仍是单条条件）
                "(material.tenant_id = :a); DROP TABLE material", Map.of("a", 1L)).forMyBatis());
        assertThrows(IllegalArgumentException.class, () -> new RowPermissionPredicate(
                "(material.tenant_id = :a) -- 注释", Map.of("a", 1L)).forMyBatis());
    }

    @Test
    @DisplayName("换写是幂等的：已经写成 #{} 的片段再过一次不会被判成「参数没被引用」")
    void translationIsIdempotent() {
        RowPermissionPredicate once = new RowPermissionPredicate(
                "(material.tenant_id = :dpRule0)", Map.of("dpRule0", 7L)).forMyBatis();

        assertEquals(once.sql(), once.forMyBatis().sql());
        // 但"参数确实没被引用"仍然要红：幂等只认"每个键都在 #{} 里"这一种形状
        assertThrows(IllegalArgumentException.class, () -> new RowPermissionPredicate(
                "(material.tenant_id = #{dpRule0})", Map.of("dpRule0", 7L, "dpRule1", 8L)).forMyBatis());
    }

    @Test
    @DisplayName("参数对象里的片段键名由常量单点决定（漂了就没人对得上模板字符串）")
    void statementParametersCarryFragmentUnderTheContractKey() {
        Map<String, Object> statement = new RowPermissionPredicate(
                "(material.tenant_id = :dpRule0)", Map.of("dpRule0", 7L)).myBatisStatementParameters();

        assertEquals(Set.of("fragment", "dpRule0"), statement.keySet());
        assertEquals("(material.tenant_id = #{dpRule0})", statement.get(RowPermissionPredicate.FRAGMENT_KEY));
        assertEquals("fragment", RowPermissionPredicate.FRAGMENT_KEY);
    }
}
