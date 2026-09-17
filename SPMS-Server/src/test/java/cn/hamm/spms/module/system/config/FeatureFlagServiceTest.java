package cn.hamm.spms.module.system.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>FeatureFlagService 契约测试（灰度决策核心）</h1>
 *
 * <p>锁住灰度引擎的语义：全局开关、百分比放量（sticky）、白/黑名单、默认值回落、非法规则不放大爆炸面。</p>
 */
class FeatureFlagServiceTest {

    @Test
    @DisplayName("全局开关：on/1/true 开启，off/0/false 关闭")
    void globalSwitch() {
        assertTrue(FeatureFlagService.decide("on", "t1", false));
        assertTrue(FeatureFlagService.decide("1", "t1", false));
        assertTrue(FeatureFlagService.decide("TRUE", "t1", false));
        assertFalse(FeatureFlagService.decide("off", "t1", true));
        assertFalse(FeatureFlagService.decide("0", "t1", true));
        assertFalse(FeatureFlagService.decide("False", "t1", true));
    }

    @Test
    @DisplayName("百分比放量：同一 scope 结果稳定（sticky），且覆盖 0%/100% 边界")
    void percentageIsSticky() {
        assertFalse(FeatureFlagService.decide("pct:0", "t1", true));   // 0% 恒关
        assertTrue(FeatureFlagService.decide("pct:100", "t1", false)); // 100% 恒开

        // sticky：同一 scope 多次判定结果一致
        boolean first = FeatureFlagService.decide("pct:50", "tenant-42", false);
        for (int i = 0; i < 20; i++) {
            assertTrue(FeatureFlagService.decide("pct:50", "tenant-42", false) == first);
        }
    }

    @Test
    @DisplayName("白名单：仅名单内 scope 开启；黑名单：名单内 scope 关闭")
    void allowDeny() {
        assertTrue(FeatureFlagService.decide("allow:1,2,3", "2", false));
        assertFalse(FeatureFlagService.decide("allow:1,2,3", "9", false));
        assertFalse(FeatureFlagService.decide("deny:4,5", "5", true));
        assertTrue(FeatureFlagService.decide("deny:4,5", "6", true));
    }

    @Test
    @DisplayName("规则缺失/空/非法时回落默认值（灰度失败不放大爆炸面）")
    void fallbackToDefault() {
        assertTrue(FeatureFlagService.decide(null, "t1", true));
        assertTrue(FeatureFlagService.decide("", "t1", true));
        assertTrue(FeatureFlagService.decide("   ", "t1", true));
        assertFalse(FeatureFlagService.decide("pct:abc", "t1", false));  // 非法百分比
        assertFalse(FeatureFlagService.decide("pct:101", "t1", false));  // 越界百分比
        assertFalse(FeatureFlagService.decide("未知规则", "t1", false));
    }

    @Test
    @DisplayName("空 scope：全局开关正常；白名单对空 scope 视为不在名单")
    void nullScope() {
        assertTrue(FeatureFlagService.decide("on", null, false));
        assertFalse(FeatureFlagService.decide("allow:1", null, true));
        // 百分比对空 scope 仍是稳定桶（不抛错）
        assertTrue(FeatureFlagService.decide("pct:100", null, false));
    }

    @Test
    @DisplayName("桶函数：确定性且落在 [0,100) 区间")
    void bucketRangeAndDeterminism() {
        for (String scope : new String[]{"t1", "t2", "tenant-42", "", "用户A"}) {
            int b = FeatureFlagService.bucket(scope);
            assertTrue(b >= 0 && b < 100, "桶值越界: " + b);
            assertTrue(FeatureFlagService.bucket(scope) == b, "桶值不稳定");
        }
    }
}
