package cn.hamm.spms.module.personnel.department;

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
 * <h1>部门树索引（进程内快照 + 显式失效）</h1>
 *
 * <p>权限判定要求"配在父部门的策略覆盖子部门"，就绕不开"某个部门的后代有哪些"。这件事有两个坑：
 * <b>每次判定都查库</b>会把它挂在最热的路径上（每个请求至少一次、每条策略一次），
 * <b>缓存放 Redis</b>则会让"改了部门树但某个节点还没过期"直接变成可见范围不一致。这里取中间态：</p>
 *
 * <ul>
 *   <li><b>一次读全表</b>建邻接表：部门是量级极小的表（几十到几百行），读全表比逐层查库便宜得多；</li>
 *   <li><b>进程内快照 + 短 TTL</b>：默认 300 秒；</li>
 *   <li><b>显式失效</b>：部门增删改会立刻打掉本进程快照，并通过既有的 Redis 广播打掉其他节点的
 *       （见 {@link DepartmentService}）。Redis 不可用时退化成本进程的 TTL —— 窗口有限，
 *       而不是永久不一致。</li>
 * </ul>
 *
 * <p><b>失败方向</b>：读不到表（迁移中、库挂了）时保留上一次的快照；从未成功过则返回"只含种子"的
 * 闭包，等价于"不做展开" —— 少看数据可以忍，多看数据不行。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentHierarchyIndex {

    private final DepartmentRepository departmentRepository;

    private final DataPermissionProperties properties;

    /**
     * 当前快照（volatile：读路径无锁，重建时整体替换）
     */
    private volatile Snapshot snapshot = Snapshot.unusable();

    /**
     * 一棵树的快照
     *
     * @param childrenByParent 父到子邻接表
     * @param builtAtNanos     构建时刻（用于 TTL 判定，用 nanoTime 避免系统时钟回拨把快照判成"过期在将来"）
     * @param usable           是否成功构建过
     */
    private record Snapshot(Map<Long, Set<Long>> childrenByParent, long builtAtNanos, boolean usable) {

        private static Snapshot unusable() {
            return new Snapshot(Map.of(), 0L, false);
        }
    }

    /**
     * 展开若干种子部门的闭包（含种子本身）
     *
     * @param seeds 种子部门 ID，可为 null / 空
     * @return 闭包；未开启层级能力或快照不可用时返回"只含种子"
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
     * 某个部门的全部后代（<b>不含</b>自身，与 {@code DepartmentService#getListByParentId} 的既有语义一致）
     *
     * @param parentId 父部门 ID
     * @return 可变的后代 ID 集合（调用方会往里补自己）
     */
    public @NotNull Set<Long> descendantsOf(long parentId) {
        Snapshot current = current();
        if (!current.usable()) {
            return new LinkedHashSet<>();
        }
        return HierarchyClosure.expand(Set.of(parentId), current.childrenByParent(), maxDepth())
                .idsWithout(Set.of(parentId));
    }

    /**
     * 快照是否可用（供诊断与测试断言，不承载权限语义）
     *
     * @return 成功构建过返回 true
     */
    public boolean isUsable() {
        return snapshot.usable();
    }

    /**
     * 显式失效：下一次访问时重建
     *
     * <p>这里<b>不用</b>"把时间戳推成很久以前"的技巧 —— {@code System.nanoTime()} 是 long，
     * 拿 {@code Long.MIN_VALUE} 当"必然过期"的哨兵会在减法上溢出，结果恰好是"永不过期"。
     * 直接换成不可用快照、由读路径重建，代价是丢掉旧数据（降级成"不展开"），
     * 换来的是没有时钟语义可算错。</p>
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
            // 显式关掉缓存：每次判定都重读全表，留给"部门树到底缓存了什么"这类排错现场
            return true;
        }
        long ageNanos = System.nanoTime() - local.builtAtNanos();
        return ageNanos > ttlSeconds * 1_000_000_000L;
    }

    /**
     * 重建快照（同一时刻只让一个线程去读库，其余线程继续用旧快照，不做请求排队）
     */
    private synchronized @NotNull Snapshot rebuild(@NotNull Snapshot previous) {
        long now = System.nanoTime();
        Snapshot latest = snapshot;
        if (latest != previous) {
            // 走到这里说明有人先一步重建完成了，别重复读全表
            return latest;
        }
        try {
            Map<Long, Long> parentById = new HashMap<>();
            for (DepartmentEntity department : departmentRepository.selectList(null)) {
                if (department == null || department.getId() == null) {
                    continue;
                }
                parentById.put(department.getId(), department.getParentId());
            }
            Snapshot built = new Snapshot(HierarchyClosure.childrenIndex(parentById), now, true);
            snapshot = built;
            log.debug("部门树快照重建完成：节点 {} 个", parentById.size());
            return built;
        } catch (RuntimeException e) {
            // 读不到就退回旧快照（或"不展开"）：权限范围宁可少给，也不能因为一次 DB 抖动放大
            log.warn("部门树快照重建失败，沿用旧快照（可能已过期）: {}", e.getMessage());
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
