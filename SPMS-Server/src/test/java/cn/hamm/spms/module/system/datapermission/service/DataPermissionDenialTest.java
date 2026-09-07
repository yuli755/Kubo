package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拒答出口的测试
 *
 * <p>这里没有 Mockito：审计用的是「记录调用的真子类」。原因是这一格要钉的正是<em>顺序</em> ——
 * 先留痕、再抛出。mock 只能回答"有没有被调用"，答不了"是在抛之前还是之后"，
 * 而顺序错了（先抛后记）在异常路径上等于这笔账永远不会出现。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataPermissionDenial · 拒答出口")
class DataPermissionDenialTest {

    /**
     * 记下每次 deny 的最简审计（构造参数全传 null：本类只用到被覆写的 deny）
     */
    private static final class RecordingAudit extends DataPermissionAudit {

        private final List<String> calls = new ArrayList<>();

        private RecordingAudit() {
            super(null, null, null);
        }

        @Override
        public void deny(String resourceKey, String reason, String detail) {
            calls.add(resourceKey + "|" + reason + "|" + detail);
        }
    }

    @Test
    @DisplayName("留痕先于抛出：顺序反过来就等于异常路径上这笔账不出现")
    void recordsBeforeThrowing() {
        RecordingAudit audit = new RecordingAudit();

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> DataPermissionDenial.denied(audit, "wms.input", DataPermissionAudit.REASON_WRITE_DENIED,
                        "id=9 action=UPDATE", "数据权限不足：测试用的拒答说明"));

        assertEquals(1, audit.calls.size(), audit.calls.toString());
        assertEquals("wms.input|" + DataPermissionAudit.REASON_WRITE_DENIED + "|id=9 action=UPDATE", audit.calls.get(0));
        assertTrue(error.getMessage().contains("数据权限不足"), error.getMessage());
    }

    @Test
    @DisplayName("审计组件缺失也要拒：打点坏了不能变成放行")
    void nullAuditStillDenies() {
        assertThrows(RuntimeException.class,
                () -> DataPermissionDenial.denied(null, "wms.input", DataPermissionAudit.REASON_UNREGISTERED,
                        "detail", "数据权限不足：没有审计组件也要拒"));
    }

    @Test
    @DisplayName("运维明细只进审计，不混进给用户的那句话")
    void detailStaysOutOfUserMessage() {
        RecordingAudit audit = new RecordingAudit();

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> DataPermissionDenial.denied(audit, "personnel.user", DataPermissionAudit.REASON_ROW_NOT_WRITABLE,
                        "internal-only-user=42", "数据权限不足：这条记录不在你的范围内"));

        assertTrue(audit.calls.get(0).contains("internal-only-user=42"), audit.calls.toString());
        assertFalse(error.getMessage().contains("internal-only-user=42"),
                "detail 是运维面（含 ID、动作、用户名），message 是用户面，混在一起迟早把别人的信息带出去");
    }
}
