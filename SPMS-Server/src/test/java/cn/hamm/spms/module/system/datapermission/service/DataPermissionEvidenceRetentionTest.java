package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionOrmParityRepository;
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
 * <h1>数据权限证据留存边界</h1>
 *
 * <p>这里不启动定时任务：留存天数是配置判据，不能因为测试库没有观测表就把时间算法一起测成假绿。
 * 清理覆盖面的用例只验证「到期清理会向三张证据表各发一次删除」，不真连库 —— 连库删多少行是
 * 各 {@code *Repository.deleteExpiredBefore} 的原生 SQL 职责，由 DDL 对账与迁移幂等测试覆盖。</p>
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
    private DataPermissionDiffRepository diff;

    @Mock
    private DataPermissionOrmParityRepository parity;

    @Mock
    private DataPermissionDenyLogRepository denyLog;

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
    @DisplayName("到期清理覆盖三张证据表：diff / orm-parity / deny-log")
    void purgeCoversAllThreeEvidenceTables() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.getShadow().setRetentionCleanupEnabled(true);

        given(diffProvider.getIfAvailable()).willReturn(diff);
        given(parityProvider.getIfAvailable()).willReturn(parity);
        given(denyLogProvider.getIfAvailable()).willReturn(denyLog);

        DataPermissionEvidenceRetention retention =
                new DataPermissionEvidenceRetention(properties, diffProvider, parityProvider, denyLogProvider);
        retention.purgeExpiredEvidence();

        verify(diff).deleteExpiredBefore(anyLong());
        verify(parity).deleteExpiredBefore(anyLong());
        verify(denyLog).deleteExpiredBefore(anyLong());
    }
}
