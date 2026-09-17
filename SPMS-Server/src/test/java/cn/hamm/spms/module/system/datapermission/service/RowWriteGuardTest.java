package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * <h1>写侧行级守卫的判定用例</h1>
 *
 * <p>这一档的失败模式与读侧相反：读侧算错是"少看几行"，写侧算错是"改不动单"或"<b>不该改的改掉了</b>"。
 * 所以这里既断言"该拒的拒"，也断言<b>"不该问的时候一次都不问"</b>—— 每次保存多打一条 COUNT，
 * 在未纳管的资源上是纯成本，而"默认关"这个决定的全部意义就在于此。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("RowWriteGuard · 这一行归不归你管")
class RowWriteGuardTest {

    private static final String KEY = "wms.input";

    /**
     * 可编程探针：记录被问了几次，并回放预设结论
     */
    private static final class Probe implements RowWriteGuard.VisibilityProbe {

        private int calls;
        private boolean visible = true;
        private RuntimeException error;

        @Override
        public boolean visible(String resourceKey, Class<?> entityClass, DataPermissionAction action, Long id) {
            calls++;
            assertEquals(KEY, resourceKey);
            assertEquals(Long.valueOf(9L), id);
            if (error != null) {
                throw error;
            }
            return visible;
        }
    }

    private Probe probe;
    private DataPermissionProperties properties;
    private DataPermissionAudit audit;
    private RowWriteGuard guard;

    @BeforeEach
    void setUp() {
        probe = new Probe();
        properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        properties.getRowWrite().setEnabled(true);
        audit = mock(DataPermissionAudit.class);
        guard = new RowWriteGuard(properties, audit, probe);
        PermissionContextHolder.open(context(false));
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private static PermissionContext context(boolean bypass) {
        return new PermissionContext(1L, 100L, Set.of(7L), Set.of(3L), Set.of(5L), Set.of("buyer"), bypass);
    }

    private static DataResourceEntity row(Long id) {
        return new DataResourceEntity().setId(id).setResourceKey(KEY);
    }

    @Test
    @DisplayName("行在视野里 → 放行，且不打审计（正常保存必须是零噪音）")
    void visibleRowPassesSilently() {
        guard.enforce(row(9L), KEY, DataPermissionAction.UPDATE);

        assertEquals(1, probe.calls);
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("行不在视野里 + enforce → 拒绝写入并记 deny")
    void invisibleRowIsDeniedInEnforce() {
        probe.visible = false;

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> guard.enforce(row(9L), KEY, DataPermissionAction.DELETE));
        assertTrue(ex.getMessage().contains("不在你当前的数据范围内"), ex.getMessage());
        verify(audit).deny(eq(KEY), eq(DataPermissionAudit.REASON_ROW_NOT_WRITABLE), contains("id=9"));
    }

    @Test
    @DisplayName("行不在视野里 + shadow → 只记「本应拒绝」，不改变结果")
    void invisibleRowOnlyAuditsInShadow() {
        probe.visible = false;
        properties.setMode(DataPermissionProperties.Mode.SHADOW);

        guard.enforce(row(9L), KEY, DataPermissionAction.UPDATE);

        verify(audit).shadow(eq(KEY), eq(DataPermissionAudit.REASON_ROW_NOT_WRITABLE), contains("本应拒绝"));
    }

    @Test
    @DisplayName("探针异常按「不可见」处理：坏了就放行等于把守卫摘掉")
    void probeFailureFailsClosed() {
        probe.error = new IllegalStateException("条件生成失败");

        assertThrows(RuntimeException.class, () -> guard.enforce(row(9L), KEY, DataPermissionAction.UPDATE));
        verify(audit).deny(eq(KEY), eq(DataPermissionAudit.REASON_PARSE_FAILED), anyString());
    }

    @Test
    @DisplayName("开关关闭 → 一次都不问（默认关就要真的零成本）")
    void disabledSwitchSkipsProbeEntirely() {
        properties.getRowWrite().setEnabled(false);
        probe.visible = false;

        guard.enforce(row(9L), KEY, DataPermissionAction.UPDATE);

        assertEquals(0, probe.calls);
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("全局 OFF 短路：连探针都不问（回滚开关必须真的回滚）")
    void globalOffShortCircuits() {
        properties.setMode(DataPermissionProperties.Mode.OFF);
        probe.visible = false;

        guard.enforce(row(9L), KEY, DataPermissionAction.DELETE);

        assertEquals(0, probe.calls);
    }

    @Test
    @DisplayName("新增与读类动作不参与：新记录没有既存行可判")
    void createAndReadActionsAreNotRowChecked() {
        probe.visible = false;

        guard.enforce(row(null), KEY, DataPermissionAction.CREATE);
        guard.enforce(row(9L), KEY, DataPermissionAction.QUERY);
        guard.enforce(row(9L), KEY, DataPermissionAction.EXPORT);

        assertEquals(0, probe.calls);
    }

    @Test
    @DisplayName("无上下文 / bypass / 未纳管资源 / 无主键 → 一律不问，也不报错")
    void nonInteractivePathsAreLeftAlone() {
        probe.visible = false;

        PermissionContextHolder.clear();
        guard.enforce(row(9L), KEY, DataPermissionAction.UPDATE);
        PermissionContextHolder.open(context(true));
        guard.enforce(row(9L), KEY, DataPermissionAction.UPDATE);
        PermissionContextHolder.open(context(false));
        guard.enforce(row(9L), null, DataPermissionAction.UPDATE);
        guard.enforce(row(null), KEY, DataPermissionAction.UPDATE);
        guard.enforce(null, KEY, DataPermissionAction.UPDATE);

        assertEquals(0, probe.calls);
    }
}
