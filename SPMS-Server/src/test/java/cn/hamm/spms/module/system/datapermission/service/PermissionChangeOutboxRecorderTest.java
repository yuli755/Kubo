package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.PermissionChangeOutboxEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionChangedEvent;
import cn.hamm.spms.module.system.datapermission.repository.PermissionChangeOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

import static cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction.QUERY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * <h1>权限变更 Outbox 记录器的用例</h1>
 *
 * <p>钉四件事：事件字段原样落成行、状态初值为 PENDING、幂等键含类型与作用域、
 * 重复键不抛（幂等跳过）。派发与退避另有 {@link PermissionChangeOutboxBackoffTest} 锁规则。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("PermissionChangeOutboxRecorder · 变更落库")
@ExtendWith(MockitoExtension.class)
class PermissionChangeOutboxRecorderTest {

    @Mock
    private PermissionChangeOutboxRepository repository;

    private PermissionChangeOutboxRecorder recorder() {
        return new PermissionChangeOutboxRecorder(repository);
    }

    private PermissionChangeOutboxEntity captured() {
        ArgumentCaptor<PermissionChangeOutboxEntity> captor = ArgumentCaptor.forClass(PermissionChangeOutboxEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("策略变更：eventType/aggregateType/aggregateId/status/payload 原样落行")
    void policyChangeMapsAllFields() {
        DataPermissionChangedEvent event =
                DataPermissionChangedEvent.policyChanged("wms.input", 42L, Set.of(QUERY));

        recorder().record(event);

        PermissionChangeOutboxEntity row = captured();
        assertEquals("POLICY_CHANGED", row.getEventType());
        assertEquals("DATA_POLICY", row.getAggregateType());
        assertEquals(42L, row.getAggregateId());
        assertEquals("PENDING", row.getStatus());
        assertEquals(0, row.getRetryCount());
        assertNotNull(row.getPayload());
        assertTrue(row.getPayload().contains("wms.input"), row.getPayload());
        assertTrue(row.getEventKey().startsWith("POLICY_CHANGED|policy:42|"), row.getEventKey());
    }

    @Test
    @DisplayName("资源变更：聚合类型 DATA_RESOURCE、无数值主键记 0")
    void resourceChangeMapsAggregate() {
        recorder().record(DataPermissionChangedEvent.resourceChanged("wms.input"));

        PermissionChangeOutboxEntity row = captured();
        assertEquals("DATA_RESOURCE", row.getAggregateType());
        assertEquals(0L, row.getAggregateId());
        assertEquals("RESOURCE_CHANGED", row.getEventType());
    }

    @Test
    @DisplayName("字段策略变更：聚合类型 DATA_FIELD_POLICY")
    void fieldPolicyChangeMapsAggregate() {
        recorder().record(DataPermissionChangedEvent.fieldPolicyChanged("wms.input"));

        PermissionChangeOutboxEntity row = captured();
        assertEquals("DATA_FIELD_POLICY", row.getAggregateType());
        assertEquals(0L, row.getAggregateId());
        assertEquals("FIELD_POLICY_CHANGED", row.getEventType());
    }

    @Test
    @DisplayName("全量刷新：聚合类型 FULL_REFRESH")
    void fullRefreshMapsAggregate() {
        recorder().record(DataPermissionChangedEvent.fullRefresh());

        PermissionChangeOutboxEntity row = captured();
        assertEquals("FULL_REFRESH", row.getAggregateType());
        assertEquals(0L, row.getAggregateId());
        assertEquals("FULL_REFRESH", row.getEventType());
    }

    @Test
    @DisplayName("重复事件键（唯一约束冲突）幂等跳过，不抛")
    void duplicateKeyIsSkipped() {
        given(repository.save(any())).willThrow(new DataIntegrityViolationException("dup"));

        assertDoesNotThrow(() -> recorder().record(DataPermissionChangedEvent.fullRefresh()));
    }
}
