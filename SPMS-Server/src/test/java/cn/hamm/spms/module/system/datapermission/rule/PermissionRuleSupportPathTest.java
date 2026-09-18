package cn.hamm.spms.module.system.datapermission.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>登记名 → 候选名的规则</h1>
 *
 * <p>这里钉的是"列名登记法"与"属性名登记法"都必须能对上。它之所以值得单独立一个文件：
 * {@code data_resource.tenant_column} 的缺省值就是列名风格的 {@code tenant_id}，而运营看到的是
 * 数据库里的列，几乎必然按列名填；策略的 {@code condition_json} 里则写满了属性名 ——
 * 填对了名字却静默不生效，是这套配置最容易踩的空转。</p>
 *
 * <p><b>J4（2026-09-18）</b>：本类原来还测 {@code PermissionRuleSupport#findPath(Root, …)}
 * （"这个名字在 JPA 实体上取不舍得到"），那一半随 {@code spring-boot-starter-data-jpa} 删除 ——
 * 现在没有实体元模型可问，能不能绑由 SQL 渲染阶段按列白名单决定。留下的是纯字符串规则，
 * 因此本类不再需要 Mockito、也不再有 JPA 类型出现在签名里。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("PermissionRuleSupport · 列名与属性名互认")
class PermissionRuleSupportPathTest {

    @Test
    @DisplayName("登记列名 → 候选名是 原名 + camelCase")
    void columnStyleProducesCamelCandidate() {
        assertEquals(List.of("tenant_id", "tenantId"), PermissionRuleSupport.pathCandidates("tenant_id"));
    }

    @Test
    @DisplayName("登记属性名 → 候选名是 原名 + snake_case（反方向也要能救）")
    void attributeStyleProducesSnakeCandidate() {
        assertEquals(List.of("tenantId", "tenant_id"), PermissionRuleSupport.pathCandidates("tenantId"));
    }

    @Test
    @DisplayName("空白与 null 输入 → 不抛异常，候选名去空白且不重复")
    void blankInputsAreSafe() {
        assertEquals(List.of(), PermissionRuleSupport.pathCandidates(null));
        assertEquals(List.of(), PermissionRuleSupport.pathCandidates("   "));
        assertEquals(List.of("creator_id", "creatorId"), PermissionRuleSupport.pathCandidates("  creator_id  "));
        assertEquals(List.of("id"), PermissionRuleSupport.pathCandidates("id"));
    }

    @Test
    @DisplayName("一跳的关联路径：两段都是合法标识符才算数（storage.id 合法、storage. 不合法）")
    void associationPathIsTwoIdentifiers() {
        assertEquals(List.of("storage", "id"), PermissionRuleSupport.pathSegments("storage.id"));
        assertEquals(List.of("creatorId"), PermissionRuleSupport.pathSegments("creatorId"));
        assertTrue(PermissionRuleSupport.isPath("storage.id"));
        assertFalse(PermissionRuleSupport.isPath("storage."), "尾部空段必须被拒，不许 split 悄悄吞掉");
        assertFalse(PermissionRuleSupport.isPath("storage.factory.id"),
                "超过一跳就不是本表外键列能表达的了：多展开一层等于宣布一个假的能力");
        // SQL-IGNORE: 恶意输入样例（断言它连"路径"这一关都过不去，不会被拆成标识符）
        assertFalse(PermissionRuleSupport.isPath("storage; DROP TABLE x"), "这不是路径，是注入形状");
    }

    @Test
    @DisplayName("等价写法展开：每一段各自展开后再组合（白名单比对就吃这个）")
    void pathVariantsExpandEachSegment() {
        assertTrue(PermissionRuleSupport.pathVariants("storage_detail.id").contains("storageDetail.id"));
        assertTrue(PermissionRuleSupport.pathVariants("storageDetail.id").contains("storage_detail.id"));
        assertEquals(java.util.Set.of(), PermissionRuleSupport.pathVariants("a..b"));
    }
}
