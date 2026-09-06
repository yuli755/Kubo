package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>未落地维度拦截 + 掩码回写防护</h1>
 *
 * <p>两条都是"看起来配了、其实没生效"的守门人，所以断言的是<b>拦截条件本身</b>：
 * 该拒的必须拒（FACTORY / factoryIds / 掩码值），不该误伤的必须放行
 * （{@code a*b} 这类真实文本、单个星号、正常手机号）。</p>
 *
 * @author Hamm.cn
 */
class DimensionAndMaskedWriteGuardTest {

    @Test
    void factorySubjectIsRejectedWhileDimensionIsUnimplemented() {
        assertFalse(DimensionGuard.FACTORY_AVAILABLE, "user_factory 建表前该维度必须保持不可用");
        assertNotNull(DimensionGuard.rejectUnavailableSubject("FACTORY"), "FACTORY 策略必须被拒");
        assertNotNull(DimensionGuard.rejectUnavailableSubject("factory"), "大小写不敏感");
        assertNull(DimensionGuard.rejectUnavailableSubject("DEPARTMENT"), "已落地维度不受影响");
        assertNull(DimensionGuard.rejectUnavailableSubject("TENANT"));
        assertNull(DimensionGuard.rejectUnavailableSubject(null));
    }

    @Test
    void factoryIdsInConditionTreeIsRejected() {
        String reason = DimensionGuard.rejectUnavailableCondition(
                "{\"op\":\"IN\",\"field\":\"factoryIds\",\"value\":[1,2]}");
        assertNotNull(reason, "条件树引用恒空变量会被渲染成恒假条件（全员空结果），必须拒");
        assertTrue(reason.contains("departmentIds"), "报错要给可用的替代维度");
        assertNull(DimensionGuard.rejectUnavailableCondition("{}"));
        assertNull(DimensionGuard.rejectUnavailableCondition(null));
        assertNull(DimensionGuard.rejectUnavailableCondition("{\"field\":\"tenantId\"}"));
    }

    @Test
    void maskedPlaceholdersAreRecognised() {
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("138****000"));
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("****"));
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("h:ab12cd34ef56"), "后端 HASH 的产出同样不能回写");
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("1******0"), "PARTIAL 的产出");
    }

    @Test
    void realValuesAreNotMistakenForMasks() {
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder("a*b"), "单个星号是真实文本");
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder("13800138000"));
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder(""));
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder(null));
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder(1999.5), "数值没有星号可言");
    }

    @Test
    void keepStoredOnlyWhenIncomingIsMasked() {
        assertEquals("13900139000",
                MaskedWriteGuard.keepStoredWhenMasked("手机号", "13900139000", "13800138000"),
                "用户真的改了手机号时必须采用新值");
        assertEquals("13800138000",
                MaskedWriteGuard.keepStoredWhenMasked("手机号", "138****000", "13800138000"),
                "提交值是掩码则保留库中原值");
        assertNull(MaskedWriteGuard.keepStoredWhenMasked("手机号", "138****000", null),
                "库里本来就没有值时保持为空，不要写入掩码");
    }

    /**
     * 已知缺口的存在性测试：非星号类自定义表达式（如 maskExpression="已隐藏"）认不出来。
     *
     * <p>不粉饰成"已覆盖"：真要精确判定必须拿"该字段生效策略的表达式"来比，
     * 那是带权限上下文的查询，写侧目前是纯文本判断。施工图 §15 已列为待办。</p>
     */
    @Test
    void customExpressionWithoutStarsIsNotRecognised_yet() {
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder("已隐藏"));
    }
}
