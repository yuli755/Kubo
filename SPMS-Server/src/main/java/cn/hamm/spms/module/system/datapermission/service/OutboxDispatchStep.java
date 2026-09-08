package cn.hamm.spms.module.system.datapermission.service;

/**
 * <h1>权限变更 Outbox 投递状态迁移（纯函数）</h1>
 *
 * <p>把「投递成功 → DONE / 投递失败 → 退避或死信」钉成一份可脱离容器、不连 Redis 就能单测的纯逻辑。
 * {@link PermissionChangeOutboxDispatcher} 只负责拉行 + 投递 + 落状态，<b>状态迁移只许调这里</b>，
 * 不得再抄一份「重试几次算死信」——重试阈值与退避都由 {@link PermissionChangeOutboxBackoff} 单源。</p>
 *
 * @author Hamm.cn
 */
public final class OutboxDispatchStep {

    private OutboxDispatchStep() {
    }

    /**
     * 一次投递的状态迁移结果
     *
     * @param status      迁移后的状态（DONE / PENDING / DEAD）
     * @param retryCount  迁移后的重试次数；{@code null} 表示不变
     * @param nextRetryAt 下次重试时间（毫秒）；{@code null} 表示不再排程
     * @param processedAt 处理完成时间（毫秒）；仅 DONE 有值
     * @param lastError   最后错误；仅失败路径有值
     */
    public record Result(String status, Integer retryCount, Long nextRetryAt, Long processedAt, String lastError) {
    }

    /**
     * 投递成功：置 DONE，记处理时间
     *
     * @param nowMillis 当前时间戳
     * @return DONE 迁移
     */
    public static Result markDone(long nowMillis) {
        return new Result(PermissionChangeOutboxStatus.DONE.name(), null, null, nowMillis, null);
    }

    /**
     * 投递失败：重试次数 +1，未达上限则按退避排下次重试，达上限则死信
     *
     * @param retryCount 本次失败前的已重试次数
     * @param lastError  失败原因（进 last_error）
     * @param nowMillis  当前时间戳
     * @return PENDING（含下次重试时间）或 DEAD
     */
    public static Result markFailed(int retryCount, String lastError, long nowMillis) {
        int next = retryCount + 1;
        if (PermissionChangeOutboxBackoff.dead(next)) {
            return new Result(PermissionChangeOutboxStatus.DEAD.name(), next, null, null, lastError);
        }
        return new Result(PermissionChangeOutboxStatus.PENDING.name(), next,
                PermissionChangeOutboxBackoff.nextRetryAtMillis(next, nowMillis), null, lastError);
    }
}
