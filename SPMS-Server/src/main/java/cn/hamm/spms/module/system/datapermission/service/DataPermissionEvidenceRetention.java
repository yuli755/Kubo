package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionOrmParityRepository;
import cn.hamm.spms.module.system.datapermission.repository.OperationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <h1>数据权限与操作审计证据留存</h1>
 *
 * <p>影子差异、ORM 对账、拒绝事件与操作审计都是决策与追责的证据，不是业务数据，也不应该无限增长。
 * 任务按统一的 {@code create_time} 截止线清理四张表：默认至少保留 30 天，长期归档由数据库备份/归档层
 * 先行完成，应用本身不把历史证据复制到业务库的第五张表里。</p>
 *
 * <p>清理失败只影响观测表，不影响请求链路；下一次调度会重试。四张表分别处理，是为了让一张表
 * 缺失或迁移尚未执行时，其余已经就绪的证据仍然可以按自己的周期清理。</p>
 *
 * <h2>为什么操作审计也在这里清理</h2>
 *
 * <p>{@code operation_log} 的 {@code deleteExpiredBefore} 早已写好，却长期没有任何调用方——
 * 也就是每标一个 {@code @OperationLog} 实体，就往库里添一张只增不减的表。扩大审计覆盖面之前
 * 必须先把留存策略接上，否则"多记一份审计"等于"多一份无上限的存储与备份成本"。
 * 复用同一个 cron 与同一个 {@code retention-days}，是为了让"证据保留多久"只有一个答案，
 * 而不是每张表各配一套、各自漂移。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class DataPermissionEvidenceRetention {

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private final DataPermissionProperties properties;
    private final ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider;
    private final ObjectProvider<DataPermissionOrmParityRepository> parityRepositoryProvider;
    private final ObjectProvider<DataPermissionDenyLogRepository> denyLogRepositoryProvider;
    private final ObjectProvider<OperationLogRepository> operationLogRepositoryProvider;

    public DataPermissionEvidenceRetention(
            DataPermissionProperties properties,
            ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider,
            ObjectProvider<DataPermissionOrmParityRepository> parityRepositoryProvider,
            ObjectProvider<DataPermissionDenyLogRepository> denyLogRepositoryProvider,
            ObjectProvider<OperationLogRepository> operationLogRepositoryProvider) {
        this.properties = properties;
        this.diffRepositoryProvider = diffRepositoryProvider;
        this.parityRepositoryProvider = parityRepositoryProvider;
        this.denyLogRepositoryProvider = denyLogRepositoryProvider;
        this.operationLogRepositoryProvider = operationLogRepositoryProvider;
    }

    /**
     * 每日清理到期的四类观测证据。
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
        int parityRows = deleteParity(cutoff);
        int denyRows = deleteDeny(cutoff);
        int operationRows = deleteOperationLog(cutoff);
        if (diffRows > 0 || parityRows > 0 || denyRows > 0 || operationRows > 0) {
            log.info("[data-permission][retention] 清理到期证据：diff={}，orm-parity={}，deny-log={}，operation-log={}，"
                            + "cutoff={}（保留 {} 天）",
                    diffRows, parityRows, denyRows, operationRows, cutoff, retentionDays);
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

    private int deleteParity(long cutoff) {
        DataPermissionOrmParityRepository repository = parityRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return 0;
        }
        try {
            return repository.deleteExpiredBefore(cutoff);
        }
        catch (RuntimeException ex) {
            log.warn("[data-permission][retention] 清理 ORM 对账表失败，下一轮重试：{}", ex.getMessage());
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

    private int deleteOperationLog(long cutoff) {
        OperationLogRepository repository = operationLogRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return 0;
        }
        try {
            return repository.deleteExpiredBefore(cutoff);
        }
        catch (RuntimeException ex) {
            log.warn("[data-permission][retention] 清理操作审计表失败，下一轮重试：{}", ex.getMessage());
            return 0;
        }
    }
}
