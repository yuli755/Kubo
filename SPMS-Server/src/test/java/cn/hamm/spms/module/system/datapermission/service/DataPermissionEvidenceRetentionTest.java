package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionOrmParityRepository;
import cn.hamm.spms.module.system.datapermission.repository.OperationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * <h1>数据权限与操作审计证据留存边界</h1>
 *
 * <p>这里不启动定时任务：留存天数是配置判据，不能因为测试库没有观测表就把时间算法一起测成假绿。
 * 清理覆盖面的用例只验证「到期清理会向四张证据表各发一次删除」，不真连库 —— 连库删多少行是
 * 各 {@code *Repository.deleteExpiredBefore} 的原生 SQL 职责，由 DDL 对账与迁移幂等测试覆盖。</p>
 *
 * <p>覆盖面用例存在的理由：{@code OperationLogRepository.deleteExpiredBefore} 曾经写好却无人调用，
 * 于是「多标一个 {@code @OperationLog} 实体」等于「多一张只增不减的表」，而编译、单测、集成测试
 * 全都发现不了 —— 少一条清理调用不会让任何断言变红。这条用例把「四张表都要清」钉成契约。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataPermissionEvidenceRetention · 证据留存")
@ExtendWith(MockitoExtension.class)
class DataPermissionEvidenceRetentionTest {

    @Mock
    private ObjectProvider<DataPermissionDiffRepository> diffProvider;

    @Mock
    private ObjectProvider<DataPermissionOrmParityRepository> parityProvider;

    @Mock
    private ObjectProvider<DataPermissionDenyLogRepository> denyLogProvider;

    @Mock
    private ObjectProvider<OperationLogRepository> operationLogProvider;

    @Mock
    private DataPermissionDiffRepository diff;

    @Mock
    private DataPermissionOrmParityRepository parity;

    @Mock
    private DataPermissionDenyLogRepository denyLog;

    @Mock
    private OperationLogRepository operationLog;

    @Test
    @DisplayName("默认保留 30 天，截止线使用毫秒")
    void cutoffUsesConfiguredDays() {
        long now = 1_800_000_000_000L;
        long oneDay = 24L * 60L * 60L * 1000L;

        assertEquals(now - 30L * oneDay,
                DataPermissionEvidenceRetention.cutoffEpochMillis(now, 30));
        assertEquals(30, new DataPermissionProperties().getShadow().getRetentionDays());
    }

    @Test
    @DisplayName("零天或负数不允许把证据表当成无期限清理")
    void retentionDaysMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> DataPermissionEvidenceRetention.cutoffEpochMillis(1_800_000_000_000L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> DataPermissionEvidenceRetention.cutoffEpochMillis(1_800_000_000_000L, -1));
    }

    @Test
    @DisplayName("到期清理覆盖四张证据表：diff / orm-parity / deny-log / operation-log")
    void purgeCoversAllFourEvidenceTables() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.getShadow().setRetentionCleanupEnabled(true);

        given(diffProvider.getIfAvailable()).willReturn(diff);
        given(parityProvider.getIfAvailable()).willReturn(parity);
        given(denyLogProvider.getIfAvailable()).willReturn(denyLog);
        given(operationLogProvider.getIfAvailable()).willReturn(operationLog);

        DataPermissionEvidenceRetention retention = new DataPermissionEvidenceRetention(
                properties, diffProvider, parityProvider, denyLogProvider, operationLogProvider);
        retention.purgeExpiredEvidence();

        verify(diff).deleteExpiredBefore(anyLong());
        verify(parity).deleteExpiredBefore(anyLong());
        verify(denyLog).deleteExpiredBefore(anyLong());
        // operation_log 必须一起清：漏了就是「每标一个 @OperationLog 实体，添一张只增不减的表」，
        // 而编译与其余断言都不会变红 —— 这正是它曾经被写出来却无人调用的原因
        verify(operationLog).deleteExpiredBefore(anyLong());
    }

    @Test
    @DisplayName("某张观测表缺失（迁移未执行）时，其余表仍按周期清理，且不抛异常")
    void missingEvidenceTableDoesNotBlockOthers() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.getShadow().setRetentionCleanupEnabled(true);

        given(diffProvider.getIfAvailable()).willReturn(null);
        given(parityProvider.getIfAvailable()).willReturn(parity);
        given(denyLogProvider.getIfAvailable()).willReturn(denyLog);
        given(operationLogProvider.getIfAvailable()).willReturn(operationLog);

        DataPermissionEvidenceRetention retention = new DataPermissionEvidenceRetention(
                properties, diffProvider, parityProvider, denyLogProvider, operationLogProvider);
        retention.purgeExpiredEvidence();

        verify(parity).deleteExpiredBefore(anyLong());
        verify(denyLog).deleteExpiredBefore(anyLong());
        verify(operationLog).deleteExpiredBefore(anyLong());
    }

    @Test
    @DisplayName("单张表删除失败只降级为告警：其余表照清，定时任务不中断")
    void oneTableFailureDoesNotAbortTheRun() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.getShadow().setRetentionCleanupEnabled(true);

        given(diffProvider.getIfAvailable()).willReturn(diff);
        given(parityProvider.getIfAvailable()).willReturn(parity);
        given(denyLogProvider.getIfAvailable()).willReturn(denyLog);
        given(operationLogProvider.getIfAvailable()).willReturn(operationLog);
        given(denyLog.deleteExpiredBefore(anyLong()))
                .willThrow(new RuntimeException("Table 'kubo.data_permission_deny_log' doesn't exist"));

        DataPermissionEvidenceRetention retention = new DataPermissionEvidenceRetention(
                properties, diffProvider, parityProvider, denyLogProvider, operationLogProvider);
        retention.purgeExpiredEvidence();

        verify(diff).deleteExpiredBefore(anyLong());
        verify(parity).deleteExpiredBefore(anyLong());
        // 前一张表失败不得让后面的表跳过清理（各自 try/catch，只降级为告警）
        verify(operationLog).deleteExpiredBefore(anyLong());
    }
}
