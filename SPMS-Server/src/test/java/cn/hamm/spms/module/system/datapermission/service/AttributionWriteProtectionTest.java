package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.TenantAwareEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>归属列写侧统一处置的用例</h1>
 *
 * <p>判据只有一句：更新时归属列 = 库中既存值，客户端提交什么都覆盖掉。
 * 与 {@code BaseService#protectAttribution} 的取数（回表）分开测 —— 取数接不接得上由 maskContract 门禁钉。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("AttributionWriteProtection · 归属列防篡改")
class AttributionWriteProtectionTest {

    @Test
    @DisplayName("客户端提交的 tenant/creator 一律被库中既存值覆盖")
    void submittedAttributionIsOverridden() {
        TenantAwareEntity<?> target = new TenantAwareEntity<>();
        target.setTenantId(999L);
        target.setCreatorId(888L);

        AttributionWriteProtection.restoreOnUpdate(target, 1L, 7L);

        assertEquals(1L, target.getTenantId());
        assertEquals(7L, target.getCreatorId());
    }

    @Test
    @DisplayName("既存值为 null 时同样覆盖为 null：不猜、不兜底（兜底等于把数据迁错租户）")
    void nullStoredValueRestoresToNull() {
        TenantAwareEntity<?> target = new TenantAwareEntity<>();
        target.setTenantId(999L);
        target.setCreatorId(888L);

        AttributionWriteProtection.restoreOnUpdate(target, null, null);

        assertNull(target.getTenantId());
        assertNull(target.getCreatorId());
    }

    @Test
    @DisplayName("值本就一致时是幂等的无操作")
    void restoreIsNoOpWhenAlreadyCorrect() {
        TenantAwareEntity<?> target = new TenantAwareEntity<>();
        target.setTenantId(3L);
        target.setCreatorId(5L);

        AttributionWriteProtection.restoreOnUpdate(target, 3L, 5L);

        assertEquals(3L, target.getTenantId());
        assertEquals(5L, target.getCreatorId());
    }
}
