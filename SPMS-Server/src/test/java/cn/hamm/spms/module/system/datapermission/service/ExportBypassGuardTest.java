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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

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
    void exportVisibilityListsOnlyRestrictedFieldsAndCarriesTheBackendVerdict() {
        /**
         * 预览的价值全在"结论"两个字上：如果这里只是把策略原样吐给前端，
         * 界面就得自己实现一遍"EXPORT + allowPlaintext 才算授权"，
         * 于是出现第三份判据 —— 它会与 guard 漂移，最后变成"提示说能导、后端拒了你"。
         * 所以断言的是结论：受限的 price = false（会被拒），已授权的 name 干脆不出现（它不受限）。
         */
        Map<String, DataFieldPolicyEntity> policies = new LinkedHashMap<>();
        policies.put("price", new DataFieldPolicyEntity().setFieldName("price").setAllowPlaintext(false));
        policies.put("name", new DataFieldPolicyEntity().setFieldName("name").setAllowPlaintext(true));
        given(columnMaskingService.getEffectiveMaskPolicyMap(eq(RK), any(PermissionContext.class)))
                .willReturn(policies);
        // price 没有 EXPORT 明文授权（返回 null = 连策略都没有）
        given(columnMaskingService.getEffectiveMaskPolicy(eq(RK), eq("price"), eq(ExportBypassGuard.ACTION_EXPORT),
                any(PermissionContext.class))).willReturn(null);

        Map<String, Boolean> visibility = guard.exportVisibility(RK, context());

        assertEquals(Set.of("price"), visibility.keySet(), "未受限的字段不该出现在预览里");
        assertFalse(visibility.get("price"), "没拿到 EXPORT 明文授权 ⇒ 这份文件会被整份拒掉");
    }

    @Test
    void exportVisibilityIsSilentWhenThereIsNoContext() {
        // 无上下文（内部调用/未认证）时返回空表：既不承诺"可以看"，也不谎报"会被拒"
        assertTrue(guard.exportVisibility(RK, null).isEmpty());
    }

    @Test
    void exportVisibilityReportsGrantedFieldsAsTrue() {
        given(columnMaskingService.getEffectiveMaskPolicyMap(eq(RK), any(PermissionContext.class)))
                .willReturn(Map.of("phone", new DataFieldPolicyEntity().setFieldName("phone").setAllowPlaintext(false)));
        given(columnMaskingService.getEffectiveMaskPolicy(eq(RK), eq("phone"), eq(ExportBypassGuard.ACTION_EXPORT),
                any(PermissionContext.class)))
                .willReturn(new DataFieldPolicyEntity().setFieldName("phone").setAllowPlaintext(true));

        assertEquals(Map.of("phone", Boolean.TRUE), guard.exportVisibility(RK, context()));
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
        // 两个桩都用 lenient：入参是 Set（无序），实现遇到第一个未授权字段就短路返回 false，
        // 于是"哪一个桩没被用到"取决于遍历顺序 —— 这是实现细节，不是本用例要锁的行为。
        // （曾经因此随机红：同一份代码，换个 Set.of 的哈希顺序就 UnnecessaryStubbing。）
        lenient().when(columnMaskingService.getEffectiveMaskPolicy(RK, "purchasePrice", ExportBypassGuard.ACTION_EXPORT, context()))
                .thenReturn(policy(true));
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
