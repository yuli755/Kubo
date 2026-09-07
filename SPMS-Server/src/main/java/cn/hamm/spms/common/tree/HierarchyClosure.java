package cn.hamm.spms.common.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h1>层级闭包展开（只此一份的树遍历）</h1>
 *
 * <p>部门 / 仓库 / 工厂都是 {@code parentId} 邻接表。这类"向下取全部后代"的遍历在本仓出现过两次实现：
 * 一次是 {@code DepartmentService#getListByParentId} 的<b>逐层查库递归</b>（N+1，且实测漏了把子节点
 * 收进结果集，返回恒空），一次是将来可能出现的递归 CTE。这里把它做成一个纯函数，两件事一起解决：
 * <b>只读一次全量邻接表</b>，并且<b>对脏数据免疫</b>。</p>
 *
 * <p>免疫不是客气话：{@code parentId} 是业务可编辑的普通列，没有任何约束能阻止运营把 A 挂到 A 的
 * 孙子下面（成环）或挂到自己下面（自环）。递归实现遇到环就是 {@code StackOverflowError} ——
 * 而这段遍历一旦挂在请求上下文里，一个环能让所有接口 500。所以本实现用 visited 集合 + 深度上限，
 * <b>宁可展开不完全（少给权限，安全侧）也不炸</b>，并通过 {@link Closure#truncated()}
 * 把"没展开完"这件事显式交回调用方（截断不吱声 = 制造"某人莫名其妙看不见数据"的悬案）。</p>
 *
 * @author Hamm.cn
 */
public final class HierarchyClosure {

    /**
     * 默认展开深度上限：部门树按 32 层算已经远超真实组织，超出的必然异常
     */
    public static final int DEFAULT_MAX_DEPTH = 32;

    private HierarchyClosure() {
    }

    /**
     * 一次展开的结果
     *
     * @param ids       种子 + 全部后代（顺序稳定，便于日志与断言）
     * @param truncated 是否因为超过深度上限而提前停止
     */
    public record Closure(Set<Long> ids, boolean truncated) {

        public Closure {
            ids = ids == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(ids));
        }

        /**
         * 去掉种子后的结果（"只要后代"的调用方用这个，避免自己再判一次）
         *
         * @param seeds 种子集合
         * @return 可变集合：调用方常会往里补主键，返回不可变集会当场 UnsupportedOperationException
         */
        public Set<Long> idsWithout(Set<Long> seeds) {
            Set<Long> rest = new LinkedHashSet<>(ids);
            if (seeds != null) {
                rest.removeAll(seeds);
            }
            return rest;
        }
    }

    /**
     * 由 {@code id -> parentId} 构造 {@code 父 -> 子} 邻接表
     *
     * <p>过滤两类噪声：自环（父=自己）与不存在的父引用（当作根，交给 {@code 0L} 那一桶）。
     * {@code parentId} 为 {@code null} / {@code 0} 的按根处理 —— 与建表默认值一致。</p>
     *
     * @param parentById id 到 parentId 的映射，可为 null
     * @return 父到子集合的索引（不可变）；每个子集合内部按插入顺序稳定
     */
    public static Map<Long, Set<Long>> childrenIndex(Map<Long, Long> parentById) {
        if (parentById == null || parentById.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> children = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> entry : parentById.entrySet()) {
            Long id = entry.getKey();
            if (id == null) {
                continue;
            }
            Long parent = entry.getValue();
            if (parent == null || parent.equals(id)) {
                // 根，或自环：自环不能进父桶（进了就是自己含自己，展开时无限增长）
                children.computeIfAbsent(ROOT, key -> new LinkedHashSet<>()).add(id);
                continue;
            }
            children.computeIfAbsent(parent, key -> new LinkedHashSet<>()).add(id);
        }
        Map<Long, Set<Long>> immutable = new LinkedHashMap<>();
        children.forEach((key, value) -> immutable.put(key, Collections.unmodifiableSet(new LinkedHashSet<>(value))));
        return Collections.unmodifiableMap(immutable);
    }

    /**
     * 根节点在索引里占用的虚拟父键（真实部门 ID 从 1 起，0 是建表默认值）
     */
    public static final Long ROOT = 0L;

    /**
     * 从种子向下展开
     *
     * @param seeds      起点集合（例如用户直属部门）
     * @param children   {@link #childrenIndex(Map)} 产出的邻接表
     * @param maxDepth   最大层数，&lt;=0 时取 {@link #DEFAULT_MAX_DEPTH}
     * @return 闭包（含种子本身）
     */
    public static Closure expand(Set<Long> seeds, Map<Long, Set<Long>> children, int maxDepth) {
        int limit = maxDepth <= 0 ? DEFAULT_MAX_DEPTH : maxDepth;
        Set<Long> result = new LinkedHashSet<>();
        if (seeds == null || seeds.isEmpty()) {
            return new Closure(result, false);
        }
        Deque<Long> queue = new ArrayDeque<>();
        for (Long seed : seeds) {
            if (seed != null && result.add(seed)) {
                queue.add(seed);
            }
        }
        Set<Long> visited = new LinkedHashSet<>(result);
        boolean truncated = false;
        List<Long> level = new ArrayList<>(queue);
        for (int depth = 0; depth < limit && !level.isEmpty(); depth++) {
            List<Long> next = new ArrayList<>();
            for (Long node : level) {
                Set<Long> kids = children == null ? null : children.get(node);
                if (kids == null) {
                    continue;
                }
                for (Long kid : kids) {
                    if (kid == null || !visited.add(kid)) {
                        // 已经访问过：要么是多父共有的子（正常），要么是环（脏数据）—— 都不能再往下走
                        continue;
                    }
                    result.add(kid);
                    next.add(kid);
                }
            }
            level = next;
            if (!level.isEmpty() && depth == limit - 1) {
                truncated = true;
            }
        }
        return new Closure(result, truncated);
    }

    /**
     * 用默认深度上限展开
     *
     * @param seeds    起点集合
     * @param children 邻接表
     * @return 闭包
     */
    public static Closure expand(Set<Long> seeds, Map<Long, Set<Long>> children) {
        return expand(seeds, children, DEFAULT_MAX_DEPTH);
    }
}
