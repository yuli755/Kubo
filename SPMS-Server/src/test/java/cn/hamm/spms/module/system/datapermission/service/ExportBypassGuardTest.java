package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * <h1>导出旁路收口的判定规则</h1>
 *
 * <p>分两块测：<b>URI 解析</b>（动作段与实体段）和<b>策略集合的取舍</b>
 * （哪些字段算受限、EXPORT 授权要覆盖到什么程度）。放行的总开关（mode / bypass）
 * 留在 {@code guard()} 里，靠的是配置读取，测试里不重复实现一遍 ——
 * 那只会得到"测试与实现一起错"的结果。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class ExportBypassGuardTest {

    @Mock
    private ColumnMaskingService columnMaskingService;

    @InjectMocks
    private ExportBypassGuard guard;

    private static final String RK = "asset.material";

    private PermissionContext context() {
        return new PermissionContext(1L, 1L, Set.of(), Set.of(), Set.of(), Set.of("buyer"), false);
    }

    @Test
    void onlyTheActionSegmentCountsAsExport() {
        assertEquals("export", ExportBypassGuard.exportActionOf("/api/material/export"));
        assertEquals("export", ExportBypassGuard.exportActionOf("/material/export/"), "末尾斜杠要容忍");
        assertEquals("queryExport", ExportBypassGuard.exportActionOf("/api/material/queryExport"));
        assertNull(ExportBypassGuard.exportActionOf("/api/material/getPage"));
        // 关键：不能用 contains 判动作，否则 /exportLog/list 这种业务路径会被误伤
        assertNull(ExportBypassGuard.exportActionOf("/api/exportLog/list"));
        assertNull(ExportBypassGuard.exportActionOf(null));
        assertNull(ExportBypassGuard.exportActionOf("/"));
    }

    @Test
    void entitySegmentIsTheOneBeforeTheAction() {
        assertEquals("material", ExportBypassGuard.apiSegmentOf("/api/material/export"));
        assertEquals("material", ExportBypassGuard.apiSegmentOf("/material/queryExport"));
        assertNull(ExportBypassGuard.apiSegmentOf("/api/export"), "只剩 /api 代理前缀 = 认不出实体 -> 交给上层放行");
        assertNull(ExportBypassGuard.apiSegmentOf("/export"), "路径太短同样认不出");
    }

    @Test
    void restrictedFieldsAreThoseMaskedForThisUser() {
        Map<String, DataFieldPolicyEntity> policies = new LinkedHashMap<>();
        policies.put("purchasePrice", policy(false));
        policies.put("salePrice", policy(true));
        given(columnMaskingService.getEffectiveMaskPolicyMap(RK, context())).willReturn(policies);

        assertEquals(Set.of("purchasePrice"), guard.restrictedFields(RK, context()),
                "开了明文的字段不算受限，不该因为别人被遮蔽就一起拦");
    }

    @Test
    void everyRestrictedFieldNeedsItsOwnExportGrant() {
        given(columnMaskingService.getEffectiveMaskPolicy(RK, "purchasePrice", ExportBypassGuard.ACTION_EXPORT, context()))
                .willReturn(policy(true));
        given(columnMaskingService.getEffectiveMaskPolicy(RK, "salePrice", ExportBypassGuard.ACTION_EXPORT, context()))
                .willReturn(null);

        assertFalse(guard.allGrantedForExport(RK, Set.of("purchasePrice", "salePrice"), context()),
                "导出是一整份文件：只要有一列没授权，这份文件就是泄漏，不能部分放行");
    }

    @Test
    void allGrantedWhenEveryFieldHasPlaintextExportPolicy() {
        given(columnMaskingService.getEffectiveMaskPolicy(RK, "purchasePrice", ExportBypassGuard.ACTION_EXPORT, context()))
                .willReturn(policy(true));
        given(columnMaskingService.getEffectiveMaskPolicy(RK, "salePrice", ExportBypassGuard.ACTION_EXPORT, context()))
                .willReturn(policy(true));

        assertTrue(guard.allGrantedForExport(RK, Set.of("purchasePrice", "salePrice"), context()));
    }

    @Test
    void noRestrictedFieldsMeansNothingToGrant() {
        assertFalse(guard.allGrantedForExport(RK, Set.of(), context()),
                "空集合不等于\"已授权\"；是否放行由上游 restricted 为空来决定，别让这里替它兜逻辑");
    }

    private DataFieldPolicyEntity policy(boolean allowPlaintext) {
        return new DataFieldPolicyEntity().setAllowPlaintext(allowPlaintext);
    }
}
