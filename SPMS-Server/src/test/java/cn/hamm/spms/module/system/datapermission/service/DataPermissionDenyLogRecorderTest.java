package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDenyLogEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 拒绝事件落库记录器的测试
 *
 * <p>核心要钉的是「证据」三件事：身份与档位在<em>业务线程</em>取成值（异步体不读 ThreadLocal）、
 * 落库把值原样写进实体、落库失败或缺 Bean 绝不影响拒绝结果（不抛）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataPermissionDenyLogRecorder · 拒绝事件落库")
@ExtendWith(MockitoExtension.class)
class DataPermissionDenyLogRecorderTest {

    @Mock
    private DataPermissionProperties properties;

    @Mock
    private ObjectProvider<DataPermissionDenyLogRepository> repositoryProvider;

    @Mock
    private DataPermissionDenyLogRepository repository;

    private DataPermissionDenyLogRecorder recorder;

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private DataPermissionDenyLogRecorder recorder() {
        if (recorder == null) {
            recorder = new DataPermissionDenyLogRecorder(properties, repositoryProvider);
        }
        return recorder;
    }

    @Test
    @DisplayName("身份与档位在业务线程取成值：不读 ThreadLocal 的后台线程拿不到上一请求的身份")
    void eventCapturesIdentityAndModeOnCallerThread() {
        given(properties.effectiveMode("wms.input")).willReturn(DataPermissionProperties.Mode.ENFORCE);
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(42L, 7L, Set.of(1L), Set.of(2L), Set.of(3L), Set.of("ADMIN"), false))) {
            DataPermissionDenyLogRecorder.DenyEvent event =
                    recorder().toEvent("wms.input", DataPermissionAudit.REASON_WRITE_DENIED, "id=9 action=UPDATE");

            assertEquals(42L, event.userId());
            assertEquals(7L, event.tenantId());
            assertEquals("ENFORCE", event.mode());
            assertEquals("wms.input", event.resourceKey());
            assertEquals(DataPermissionAudit.REASON_WRITE_DENIED, event.reason());
        }
    }

    @Test
    @DisplayName("无权限上下文时身份为空但不丢档位：内部调用被拒同样留档")
    void eventWithoutContextKeepsMode() {
        given(properties.effectiveMode("wms.input")).willReturn(DataPermissionProperties.Mode.SHADOW);
        DataPermissionDenyLogRecorder.DenyEvent event =
                recorder().toEvent("wms.input", DataPermissionAudit.REASON_ROW_NOT_WRITABLE, "id=1");

        assertNull(event.userId());
        assertNull(event.tenantId());
        assertEquals("SHADOW", event.mode());
    }

    @Test
    @DisplayName("落库把事件值原样写进实体")
    void persistWritesAllFields() {
        given(repositoryProvider.getIfAvailable()).willReturn(repository);
        DataPermissionDenyLogRecorder.DenyEvent event =
                new DataPermissionDenyLogRecorder.DenyEvent(
                        "wms.output", DataPermissionAudit.REASON_UNREGISTERED, "SHADOW", 42L, 7L, "detail-x");

        recorder().persist(event);

        ArgumentCaptor<DataPermissionDenyLogEntity> captor = ArgumentCaptor.forClass(DataPermissionDenyLogEntity.class);
        verify(repository).save(captor.capture());
        DataPermissionDenyLogEntity saved = captor.getValue();
        assertEquals("wms.output", saved.getResourceKey());
        assertEquals(DataPermissionAudit.REASON_UNREGISTERED, saved.getReason());
        assertEquals("SHADOW", saved.getMode());
        assertEquals(42L, saved.getUserId());
        assertEquals(7L, saved.getTenantId());
        assertEquals("detail-x", saved.getDetail());
    }

    @Test
    @DisplayName("表缺 Bean（未注册）时静默：落库坏了不能变成拒绝被改写")
    void persistSilentlySkipsWhenRepositoryMissing() {
        given(repositoryProvider.getIfAvailable()).willReturn(null);
        DataPermissionDenyLogRecorder.DenyEvent event =
                new DataPermissionDenyLogRecorder.DenyEvent("wms.input", "x", "SHADOW", null, null, "d");

        assertDoesNotThrow(() -> recorder().persist(event));
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("写库抛异常时吞掉：证据失败只降级、不打断拒绝路径")
    void persistSwallowsWriteFailure() {
        given(repositoryProvider.getIfAvailable()).willReturn(repository);
        given(repository.save(any(DataPermissionDenyLogEntity.class))).willThrow(new RuntimeException("db down"));
        DataPermissionDenyLogRecorder.DenyEvent event =
                new DataPermissionDenyLogRecorder.DenyEvent("wms.input", "x", "ENFORCE", null, null, "d");

        assertDoesNotThrow(() -> recorder().persist(event));
    }
}
