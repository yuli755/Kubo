package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>行级策略"永不命中"盘点的用例</h1>
 *
 * <p>这些判据必须与运行期两处代码同义：{@code DataPolicyRepository} 的按动作取数、
 * {@code DataPermissionService.matchesSubject} 的主体匹配。若那两处改了语义而这里没红，
 * 说明分析器在说过期的小话 —— 所以本文件里的每条断言都对应一处真实的 else/default 分支。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("RowPolicyRiskAnalyzer · 启用却永不命中的行级策略")
class RowPolicyRiskAnalyzerTest {

    private static DataPolicyEntity row(String action, String subjectType, String roleCode, Long subjectId) {
        return new DataPolicyEntity()
                .setPolicyCode("P-1")
                .setActionName(action)
                .setSubjectType(subjectType)
                .setRoleCode(roleCode)
                .setSubjectId(subjectId);
    }

    private static List<RowPolicyRiskAnalyzer.Risk> risks(DataPolicyEntity policy, boolean factoryWired) {
        return RowPolicyRiskAnalyzer.risks(List.of(policy), factoryWired);
    }

    @Test
    @DisplayName("配置正确的 ROLE 策略 → 不报（别让面板喊狼来了）")
    void wellFormedRolePolicyIsClean() {
        assertTrue(risks(row("QUERY", "ROLE", "buyer", null), false).isEmpty());
    }

    @Test
    @DisplayName("动作名写错 → 报动作不可达，并把原值回显出来")
    void typoActionIsUnreachable() {
        List<RowPolicyRiskAnalyzer.Risk> found = risks(row("REPROT", "ROLE", "buyer", null), false);

        assertEquals(1, found.size());
        assertEquals(RowPolicyRiskAnalyzer.KIND_UNREACHABLE_ACTION, found.get(0).kind());
        assertTrue(found.get(0).detail().contains("REPROT"), found.get(0).detail());
    }

    @Test
    @DisplayName("动作留空 → 同样报，并写成「(未填)」而不是静默跳过")
    void blankActionReported() {
        List<RowPolicyRiskAnalyzer.Risk> found = risks(row("  ", "ROLE", "buyer", null), false);

        assertEquals(1, found.size());
        assertTrue(found.get(0).detail().contains("(未填)"), found.get(0).detail());
    }

    @Test
    @DisplayName("主体类型不在枚举里 → matchesSubject 走 default，恒不命中")
    void unknownSubjectTypeNeverMatches() {
        List<RowPolicyRiskAnalyzer.Risk> found = risks(row("QUERY", "ROLES", "buyer", null), false);

        assertEquals(1, found.size());
        assertEquals(RowPolicyRiskAnalyzer.KIND_UNMATCHABLE_SUBJECT, found.get(0).kind());
    }

    @Test
    @DisplayName("ROLE 缺 roleCode / USER 缺 subjectId → 都算恒不命中")
    void missingSubjectKeyNeverMatches() {
        List<RowPolicyRiskAnalyzer.Risk> role = risks(row("QUERY", "ROLE", null, null), false);
        List<RowPolicyRiskAnalyzer.Risk> user = risks(row("QUERY", "USER", null, null), false);

        assertEquals(1, role.size());
        assertTrue(role.get(0).detail().contains("roleCode"), role.get(0).detail());
        assertEquals(1, user.size());
        assertTrue(user.get(0).detail().contains("subjectId"), user.get(0).detail());
    }

    @Test
    @DisplayName("USER 带 subjectId → 不报；SYSTEM 什么都不带也不报（恒匹配）")
    void validSubjectsAreClean() {
        assertTrue(risks(row("QUERY", "USER", null, 9L), false).isEmpty());
        assertTrue(risks(row("QUERY", "SYSTEM", null, null), false).isEmpty());
    }

    @Test
    @DisplayName("FACTORY：维度未接入时报维度未接入；已接入后按 subjectId 判")
    void factoryDependsOnDimensionWiring() {
        List<RowPolicyRiskAnalyzer.Risk> off = risks(row("QUERY", "FACTORY", null, 3L), false);
        List<RowPolicyRiskAnalyzer.Risk> onWithId = risks(row("QUERY", "FACTORY", null, 3L), true);
        List<RowPolicyRiskAnalyzer.Risk> onWithoutId = risks(row("QUERY", "FACTORY", null, null), true);

        assertEquals(1, off.size());
        assertEquals(RowPolicyRiskAnalyzer.KIND_DIMENSION_NOT_WIRED, off.get(0).kind());
        assertTrue(onWithId.isEmpty());
        assertEquals(1, onWithoutId.size());
        assertEquals(RowPolicyRiskAnalyzer.KIND_UNMATCHABLE_SUBJECT, onWithoutId.get(0).kind());
    }

    @Test
    @DisplayName("同一行多个毛病 → 只报一条（先动作后主体），清单要能数")
    void onlyFirstReasonPerRow() {
        List<RowPolicyRiskAnalyzer.Risk> found = risks(row("NOPE", "ROLES", null, null), false);

        assertEquals(1, found.size());
        assertEquals(RowPolicyRiskAnalyzer.KIND_UNREACHABLE_ACTION, found.get(0).kind());
    }

    @Test
    @DisplayName("空输入 → 空结果，不抛异常")
    void nullAndEmptyInputsAreSafe() {
        assertTrue(RowPolicyRiskAnalyzer.risks(null, false).isEmpty());
        assertTrue(RowPolicyRiskAnalyzer.risks(List.of(), false).isEmpty());
    }

    @Test
    @DisplayName("condition 为 {} 不算风险：SYSTEM / ROLE 的「允许全部」是设计语义")
    void allowAllPolicyIsNotADefect() {
        DataPolicyEntity allowAll = row("QUERY", "ROLE", "buyer", null).setConditionJson("{}");

        assertTrue(risks(allowAll, false).isEmpty());
    }
}
