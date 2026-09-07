package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <h1>数据权限证据留存边界</h1>
 *
 * <p>这里只测截止线，不启动定时任务，也不碰数据库：留存天数是配置判据，
 * 不能因为测试库没有观测表就把时间算法一起测成假绿。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataPermissionEvidenceRetention · 证据留存")
class DataPermissionEvidenceRetentionTest {

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
}
