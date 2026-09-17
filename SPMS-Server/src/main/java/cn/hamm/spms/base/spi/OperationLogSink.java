package cn.hamm.spms.base.spi;

/**
 * <h1>底座向业务域要的能力：通用操作审计落库</h1>
 *
 * <p>base 的三个钩子（新增后 / 更新前暂存旧值、更新后 / 删除前）是"谁改过这条"的唯一证据来源，
 * 但审计表、异步队列、快照序列化都属于 {@code module.system.datapermission}。
 * 于是这里只留一句抽象：<b>记一次操作</b>。</p>
 *
 * <p><b>实现不许改变业务结果</b>：审计失败（缺表、队列满、序列化失败）只影响证据，
 * 不许把异常抛回业务路径 —— 否则"审计坏了"会变成"保存不了"。</p>
 *
 * @author Hamm.cn
 */
public interface OperationLogSink {
    /**
     * 动作：新增
     */
    String ACTION_CREATE = "CREATE";

    /**
     * 动作：更新
     */
    String ACTION_UPDATE = "UPDATE";

    /**
     * 动作：删除
     */
    String ACTION_DELETE = "DELETE";

    /**
     * 记一次操作审计
     *
     * @param resourceKey 资源标识（{@code data_resource.resource_key} 的口径）
     * @param action      动作（{@link #ACTION_CREATE} / {@link #ACTION_UPDATE} / {@link #ACTION_DELETE}）
     * @param targetId    目标记录 ID
     * @param before      变更前实体（可为 {@code null}）
     * @param after       变更后实体（可为 {@code null}）
     */
    void record(String resourceKey, String action, Long targetId, Object before, Object after);
}
