package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.OperationLogEntity;
import cn.hamm.spms.module.system.datapermission.repository.OperationLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 通用操作审计落库记录器的测试
 *
 * <p>钉三件事：身份与快照在<em>业务线程</em>取成值（异步体不读 ThreadLocal）、
 * 落库把事件值原样写进实体、落库失败或缺 Bean 绝不改变业务结果（不抛）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("OperationLogRecorder · 通用操作审计落库")
@ExtendWith(MockitoExtension.class)
class OperationLogRecorderTest {

    @Mock
    private ObjectProvider<OperationLogRepository> repositoryProvider;

    @Mock
    private OperationLogRepository repository;

    private OperationLogRecorder recorder;

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private OperationLogRecorder recorder() {
        if (recorder == null) {
            recorder = new OperationLogRecorder(repositoryProvider);
        }
        return recorder;
    }

    @Test
    @DisplayName("身份与快照在业务线程取成值：不读 ThreadLocal 的后台线程拿不到上一请求的身份")
    void eventCapturesIdentityAndSnapshotOnCallerThread() {
        SampleTarget before = new SampleTarget();
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(42L, 7L, Set.of(1L), Set.of(2L), Set.of(3L), Set.of("ADMIN"), false))) {
            OperationLogRecorder.OperationLogEvent event =
                    recorder().toEvent("asset.material", OperationLogRecorder.ACTION_UPDATE, 9L, before, before);

            assertEquals(42L, event.userId());
            assertEquals(7L, event.tenantId());
            assertEquals("asset.material", event.resourceKey());
            assertEquals(OperationLogRecorder.ACTION_UPDATE, event.action());
            assertEquals(9L, event.targetId());
            assertEquals("M-001", event.targetLabel());
            assertNotNull(event.beforeSnapshot());
            assertTrue(event.beforeSnapshot().contains("M-001"), "快照应含标量字段值");
            assertNotNull(event.afterSnapshot());
        }
    }

    @Test
    @DisplayName("无权限上下文时身份为空但审计不丢：内部调用同样留档")
    void eventWithoutContextKeepsAudit() {
        SampleTarget after = new SampleTarget();
        OperationLogRecorder.OperationLogEvent event =
                recorder().toEvent("mes.bom", OperationLogRecorder.ACTION_CREATE, 1L, null, after);

        assertNull(event.userId());
        assertNull(event.tenantId());
        assertEquals("mes.bom", event.resourceKey());
        assertEquals(OperationLogRecorder.ACTION_CREATE, event.action());
        assertNull(event.beforeSnapshot());
        assertNotNull(event.afterSnapshot());
    }

    @Test
    @DisplayName("落库把事件值原样写进实体")
    void persistWritesAllFields() {
        given(repositoryProvider.getIfAvailable()).willReturn(repository);
        OperationLogRecorder.OperationLogEvent event = new OperationLogRecorder.OperationLogEvent(
                "wms.input", OperationLogRecorder.ACTION_DELETE, 8L, "W-008", "{\"name\":\"old\"}", null, 42L, 7L);

        recorder().persist(event);

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(repository).save(captor.capture());
        OperationLogEntity saved = captor.getValue();
        assertEquals("wms.input", saved.getResourceKey());
        assertEquals(OperationLogRecorder.ACTION_DELETE, saved.getAction());
        assertEquals(8L, saved.getTargetId());
        assertEquals("W-008", saved.getTargetLabel());
        assertEquals("{\"name\":\"old\"}", saved.getBeforeSnapshot());
        assertNull(saved.getAfterSnapshot());
        assertEquals(42L, saved.getUserId());
        assertEquals(7L, saved.getTenantId());
    }

    @Test
    @DisplayName("表缺 Bean（未注册）时静默：落库坏了不能变成业务失败")
    void persistSilentlySkipsWhenRepositoryMissing() {
        given(repositoryProvider.getIfAvailable()).willReturn(null);
        OperationLogRecorder.OperationLogEvent event =
                new OperationLogRecorder.OperationLogEvent("wms.input", "CREATE", null, null, null, null, null, null);

        assertDoesNotThrow(() -> recorder().persist(event));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("写库抛异常时吞掉：证据失败只降级、不打断业务写动作")
    void persistSwallowsWriteFailure() {
        given(repositoryProvider.getIfAvailable()).willReturn(repository);
        given(repository.save(any(OperationLogEntity.class))).willThrow(new RuntimeException("db down"));
        OperationLogRecorder.OperationLogEvent event =
                new OperationLogRecorder.OperationLogEvent("wms.input", "UPDATE", null, null, null, null, null, null);

        assertDoesNotThrow(() -> recorder().persist(event));
    }

    /**
     * 极简测试目标：具备 code/name/id + 一个标量字段，供快照与摘要断言。
     */
    private static class SampleTarget {
        private final Long id = 9L;
        private final String code = "M-001";
        private final String name = "物料A";
        private final Long qty = 3L;

        public Long getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public Long getQty() {
            return qty;
        }
    }
}
