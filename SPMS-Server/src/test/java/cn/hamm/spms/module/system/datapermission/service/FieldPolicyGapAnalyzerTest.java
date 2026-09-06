package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>导出明文授权缺口分析的用例</h1>
 *
 * <p>这些用例守的是"管理员能不能在看板上发现自己配漏了"。它们必须与
 * {@code ExportBypassGuard} 的实际拒因保持一致：guard 用的是
 * "被遮蔽的每一个字段都要有 EXPORT 明文授权"，所以<em>缺一格</em>就要在这里报出来；
 * 反过来，本类报"无缺口"时 guard 就不该拒 —— 两边不一致会让自查面板变成误导源。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("FieldPolicyGapAnalyzer · 遮蔽与导出授权的组合完整性")
class FieldPolicyGapAnalyzerTest {

    private static DataFieldPolicyEntity masking(String field, String role) {
        return new DataFieldPolicyEntity()
                .setFieldName(field).setRoleCode(role).setActionName("QUERY").setAllowPlaintext(false);
    }

    private static DataFieldPolicyEntity grant(String field, String role) {
        return new DataFieldPolicyEntity()
                .setFieldName(field).setRoleCode(role).setActionName("EXPORT").setAllowPlaintext(true);
    }

    @Test
    @DisplayName("同角色有 EXPORT 明文授权 → 无缺口")
    void coveredByExactRole() {
        assertTrue(FieldPolicyGapAnalyzer.gaps(
                List.of(masking("price", "buyer")),
                List.of(grant("price", "buyer"))).isEmpty());
    }

    @Test
    @DisplayName("只配了遮蔽、没配导出授权 → 报缺口，且说清"缺一个就整份被拒"")
    void missingGrantIsReported() {
        List<FieldPolicyGapAnalyzer.Gap> gaps =
                FieldPolicyGapAnalyzer.gaps(List.of(masking("price", "buyer")), List.of());

        assertEquals(1, gaps.size());
        assertEquals("price", gaps.get(0).fieldName());
        assertEquals("buyer", gaps.get(0).roleCode());
        assertTrue(gaps.get(0).reason().contains("整份"), gaps.get(0).reason());
    }

    @Test
    @DisplayName("EXPORT 行存在但没允许明文 → 仍算缺口（授权语义不是"有一行就行"）")
    void exportRowWithoutPlaintextIsNotAGrant() {
        DataFieldPolicyEntity maskedExport = new DataFieldPolicyEntity()
                .setFieldName("price").setRoleCode("buyer").setActionName("EXPORT").setAllowPlaintext(false);

        assertEquals(1, FieldPolicyGapAnalyzer.gaps(List.of(masking("price", "buyer")), List.of(maskedExport)).size());
    }

    @Test
    @DisplayName("default 遮蔽 + default 授权 → 无缺口")
    void defaultPairCovers() {
        assertTrue(FieldPolicyGapAnalyzer.gaps(
                List.of(masking("phone", ColumnMaskingService.DEFAULT_ROLE_CODE)),
                List.of(grant("phone", ColumnMaskingService.DEFAULT_ROLE_CODE))).isEmpty());
    }

    @Test
    @DisplayName("roleCode 为空等价于 default（与 ColumnMaskingService 的解析一致）")
    void nullRoleMeansDefault() {
        assertTrue(FieldPolicyGapAnalyzer.gaps(
                List.of(masking("phone", null)),
                List.of(grant("phone", ColumnMaskingService.DEFAULT_ROLE_CODE))).isEmpty());
    }

    @Test
    @DisplayName("default 遮蔽只给某个角色授权 → 仍报缺口（其它角色照样被整份拒）")
    void partialGrantForDefaultMaskingStillGaps() {
        List<FieldPolicyGapAnalyzer.Gap> gaps = FieldPolicyGapAnalyzer.gaps(
                List.of(masking("phone", ColumnMaskingService.DEFAULT_ROLE_CODE)),
                List.of(grant("phone", "buyer")));

        assertEquals(1, gaps.size());
        assertEquals(ColumnMaskingService.DEFAULT_ROLE_CODE, gaps.get(0).roleCode());
        assertTrue(gaps.get(0).reason().contains("所有角色"), gaps.get(0).reason());
    }

    @Test
    @DisplayName("遮蔽本身允许明文 → 不是遮蔽，不该报")
    void plaintextQueryPolicyIsNotMasking() {
        DataFieldPolicyEntity open = new DataFieldPolicyEntity()
                .setFieldName("price").setRoleCode("buyer").setActionName("QUERY").setAllowPlaintext(true);

        assertTrue(FieldPolicyGapAnalyzer.gaps(List.of(open), List.of()).isEmpty());
    }

    @Test
    @DisplayName("同一角色同一字段重复配置 → 只报一条（清单要能数，不能刷重复行）")
    void duplicatesCollapse() {
        assertEquals(1, FieldPolicyGapAnalyzer.gaps(
                List.of(masking("price", "buyer"), masking("price", "buyer")), List.of()).size());
    }

    @Test
    @DisplayName("精确角色遮蔽可被 default 授权覆盖（与运行期的兜底方向一致）")
    void defaultGrantCoversRoleMasking() {
        assertTrue(FieldPolicyGapAnalyzer.gaps(
                List.of(masking("price", "buyer")),
                List.of(grant("price", ColumnMaskingService.DEFAULT_ROLE_CODE))).isEmpty());
    }
}
