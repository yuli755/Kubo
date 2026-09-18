package cn.hamm.spms.module.factory.storage;

import cn.hamm.spms.common.tree.HierarchyClosure;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * <h1>仓库/工厂树索引（进程内快照 + 显式失效，D13）</h1>
 *
 * <p>与 {@link cn.hamm.spms.module.personnel.department.DepartmentHierarchyIndex} 同构：
 * {@code StorageEntity.parentId} 也是一张可编辑的邻接表，工厂维度（FACTORY）要把「配在工厂
 * 节点上的策略」展开到整棵子树（工厂 → 其下所有仓库），就绕不开「某节点的后代有哪些」。
 * 沿用了部门树索引的三条纪律：</p>
 *
 * <ul>
 *   <li><b>一次读全表</b>建邻接表：storage 与 department 同量级（几十到几百行）；</li>
 *   <li><b>进程内快照 + 短 TTL</b>（沿用 {@code hierarchy.ttl-seconds}，默认 300 秒）；</li>
 *   <li><b>显式失效</b>：仓库增删改会打掉本进程快照并广播其它节点（见 {@link StorageService}）。</li>
 * </ul>
 *
 * <p><b>失败方向</b>：读不到表时退回「只含种子」，等价于不做展开 —— 少看数据可以忍，多看不行。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageHierarchyIndex {

    private final StorageRepository storageRepository;

    private final DataPermissionProperties properties;

    /**
     * 当前快照（volatile：读路径无锁，重建时整体替换）
     */
    private volatile Snapshot snapshot = Snapshot.unusable();

    /**
     * 一棵树的快照
     */
    private record Snapshot(Map<Long, Set<Long>> childrenByParent, long builtAtNanos, boolean usable) {

        private static Snapshot unusable() {
            return new Snapshot(Map.of(), 0L, false);
        }
    }

    /**
     * 展开若干种子节点的闭包（含种子本身）
     *
     * @param seeds 种子节点 ID（用户所属工厂 = storage 树顶层节点），可为 null / 空
     * @return 闭包；快照不可用时返回「只含种子」
     */
    public @NotNull HierarchyClosure.Closure expandFrom(Set<Long> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            return new HierarchyClosure.Closure(Set.of(), false);
        }
        Snapshot current = current();
        if (!current.usable()) {
            return new HierarchyClosure.Closure(new LinkedHashSet<>(seeds), false);
        }
        return HierarchyClosure.expand(seeds, current.childrenByParent(), maxDepth());
    }

    /**
     * 快照是否可用（供诊断与测试断言，不承载权限语义）
     */
    public boolean isUsable() {
        return snapshot.usable();
    }

    /**
     * 显式失效：下一次访问时重建
     *
     * <p>与部门树索引同理：不用「把时间戳推成很久以前」的哨兵技巧 —— {@code System.nanoTime()}
     * 是 long，{@code Long.MIN_VALUE} 当「必然过期」会在减法上溢出成「永不过期」。</p>
     */
    public void invalidate() {
        snapshot = Snapshot.unusable();
    }

    /**
     * 取快照：过期即重建
     */
    private @NotNull Snapshot current() {
        Snapshot local = snapshot;
        if (local.usable() && !isStale(local)) {
            return local;
        }
        return rebuild(local);
    }

    /**
     * TTL 判定
     */
    private boolean isStale(@NotNull Snapshot local) {
        long ttlSeconds = properties == null ? 0L : properties.getHierarchy().getTtlSeconds();
        if (ttlSeconds <= 0L) {
            return true;
        }
        long ageNanos = System.nanoTime() - local.builtAtNanos();
        return ageNanos > ttlSeconds * 1_000_000_000L;
    }

    /**
     * 重建快照（同一时刻只让一个线程去读库，其余线程继续用旧快照）
     */
    private synchronized @NotNull Snapshot rebuild(@NotNull Snapshot previous) {
        long now = System.nanoTime();
        Snapshot latest = snapshot;
        if (latest != previous) {
            return latest;
        }
        try {
            Map<Long, Long> parentById = new HashMap<>();
            for (StorageEntity storage : storageRepository.selectList(null)) {
                if (storage == null || storage.getId() == null) {
                    continue;
                }
                parentById.put(storage.getId(), storage.getParentId());
            }
            Snapshot built = new Snapshot(HierarchyClosure.childrenIndex(parentById), now, true);
            snapshot = built;
            log.debug("仓库/工厂树快照重建完成：节点 {} 个", parentById.size());
            return built;
        } catch (RuntimeException e) {
            log.warn("仓库/工厂树快照重建失败，沿用旧快照（可能已过期）: {}", e.getMessage());
            Snapshot degraded = new Snapshot(previous.childrenByParent(), now, previous.usable());
            snapshot = degraded;
            return degraded;
        }
    }

    /**
     * 深度上限
     */
    private int maxDepth() {
        return properties == null ? HierarchyClosure.DEFAULT_MAX_DEPTH : properties.getHierarchy().getMaxDepth();
    }
}
