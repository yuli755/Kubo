package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <h1>数据权限证据留存</h1>
 *
 * <p>影子差异（{@code data_permission_diff_log}）与拒绝日志都是放开决策的证据，不是业务数据，
 * 也不应该无限增长。任务按统一的 {@code create_time} 截止线清理这两张表：默认至少保留 30 天，
 * 长期归档由数据库备份/归档层先行完成，应用本身不把历史证据复制到业务库的第三张表里。</p>
 *
 * <p>清理失败只影响观测表，不影响请求链路；下一次调度会重试。两个仓库分别处理，是为了让一张表
 * 缺失或迁移尚未执行时，另一张已经就绪的证据仍然可以按自己的周期清理。</p>
 *
 * <p><b>2026-09-18 J4</b>：原先还清理 {@code data_permission_orm_parity_log}（JPA 侧 / Mapper 侧的
 * 行数对账）；那张表随 {@code spring-boot-starter-data-jpa} 一起退场 —— 只剩一条读取与渲染路径之后，
 * "两套 ORM 数出来一不一样"这个问题本身不存在了。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class DataPermissionEvidenceRetention {

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private final DataPermissionProperties properties;
    private final ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider;
    private final ObjectProvider<DataPermissionDenyLogRepository> denyLogRepositoryProvider;

    public DataPermissionEvidenceRetention(
            DataPermissionProperties properties,
            ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider,
            ObjectProvider<DataPermissionDenyLogRepository> denyLogRepositoryProvider) {
        this.properties = properties;
        this.diffRepositoryProvider = diffRepositoryProvider;
        this.denyLogRepositoryProvider = denyLogRepositoryProvider;
    }

    /**
     * 每日清理到期的观测证据（差异表 + 拒绝日志）。
     *
     * <p>cron 由配置覆盖，默认时间与 {@link DataPermissionProperties.Shadow#retentionCleanupCron} 一致。
     * 不把任务绑定到某个运行模式：即使当前切回 OFF，也要把已经产生的历史证据按留存策略收口。</p>
     */
    @Scheduled(cron = "${kubo.data-permission.shadow.retention-cleanup-cron:0 15 3 * * *}")
    public void purgeExpiredEvidence() {
        DataPermissionProperties.Shadow shadow = properties.getShadow();
        if (shadow == null || !shadow.isRetentionCleanupEnabled()) {
            return;
        }
        int retentionDays = shadow.getRetentionDays();
        if (retentionDays <= 0) {
            log.error("[data-permission][retention] retention-days 必须为正数，已跳过本次清理：{}", retentionDays);
            return;
        }
        long cutoff = cutoffEpochMillis(System.currentTimeMillis(), retentionDays);
        int diffRows = deleteDiff(cutoff);
        int denyRows = deleteDeny(cutoff);
        if (diffRows > 0 || denyRows > 0) {
            log.info("[data-permission][retention] 清理到期证据：diff={}，deny-log={}，cutoff={}（保留 {} 天）",
                    diffRows, denyRows, cutoff, retentionDays);
        }
    }

    /**
     * 计算统一截止线，单独抽出供边界测试使用。
     *
     * @param nowEpochMillis 当前时间（毫秒）
     * @param retentionDays 需保留天数
     * @return 截止时间（该时间之前的记录可清理）
     */
    static long cutoffEpochMillis(long nowEpochMillis, int retentionDays) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retention-days must be positive");
        }
        return nowEpochMillis - Math.multiplyExact(retentionDays, MILLIS_PER_DAY);
    }

    private int deleteDiff(long cutoff) {
        DataPermissionDiffRepository repository = diffRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return 0;
        }
        try {
            return repository.deleteExpiredBefore(cutoff);
        }
        catch (RuntimeException ex) {
            log.warn("[data-permission][retention] 清理影子差异表失败，下一轮重试：{}", ex.getMessage());
            return 0;
        }
    }

    private int deleteDeny(long cutoff) {
        DataPermissionDenyLogRepository repository = denyLogRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return 0;
        }
        try {
            return repository.deleteExpiredBefore(cutoff);
        }
        catch (RuntimeException ex) {
            log.warn("[data-permission][retention] 清理拒绝事件表失败，下一轮重试：{}", ex.getMessage());
            return 0;
        }
    }
}
