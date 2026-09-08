package cn.hamm.spms.module.system.datapermission.service;

/**
 * <h1>权限变更 Outbox 重试退避（纯函数）</h1>
 *
 * <p>把「多久重试一次、多少次算死信」钉成一份可脱离容器单测的纯逻辑。
 * 当前 Dispatcher 尚未接入（P5 后置，需真实 MQ），这里的规则先落盘并锁住，
 * 接 Dispatcher 时只能调它、不得再抄一份指数退避。</p>
 *
 * <p>口径：重试次数从 0 计，每次翻倍（1s → 2s → 4s → 8s → 16s），封顶 60s；
 * {@code retryCount >= MAX_RETRY} 即判死信，不再投递。</p>
 *
 * @author Hamm.cn
 */
public final class PermissionChangeOutboxBackoff {

    /**
     * 最大重试次数（达到即死信）
     */
    public static final int MAX_RETRY = 5;

    private static final long BASE_DELAY_MILLIS = 1_000L;

    private static final long MAX_DELAY_MILLIS = 60_000L;

    private PermissionChangeOutboxBackoff() {
    }

    /**
     * 该重试次数是否已判死信
     *
     * @param retryCount 已重试次数
     * @return 达到 / 超过上限返回 true
     */
    public static boolean dead(int retryCount) {
        return retryCount >= MAX_RETRY;
    }

    /**
     * 下一次重试的绝对时间戳（毫秒）
     *
     * @param retryCount 已重试次数（0 起）
     * @param nowMillis  当前时间戳
     * @return 下次重试时间，恒大于 {@code nowMillis}
     */
    public static long nextRetryAtMillis(int retryCount, long nowMillis) {
        long shift = Math.min(Math.max(retryCount, 0), 6);
        long delay = BASE_DELAY_MILLIS << shift;
        return nowMillis + Math.min(delay, MAX_DELAY_MILLIS);
    }
}
