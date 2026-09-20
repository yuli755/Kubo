package cn.hamm.spms.framework.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * <h1>菜单、部门、仓库与结构树的组装和后代查询</h1>
 *
 * <p>空父 ID 视作根 0；保留输入顺序、不可变 children 列表和原节点引用。
 * 不把孤儿提升为根（权限菜单不能凭空增加可见入口）。迭代遍历避免深树递归溢出。</p>
 *
 * @author Hamm.cn
 */
public final class Trees {
    public static final long ROOT_ID = 0L;

    private Trees() {
    }

    /** O(n) 组树，只修改从根可达节点的 children。重复/空/根占位 ID 是无效数据，拒绝组装。 */
    public static <E extends TreeNode<E>> List<E> buildTreeList(List<E> nodes) {
        Map<Long, List<E>> byParent = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (E node : nodes) {
            Long id = Objects.requireNonNull(node.getId(), "树节点 ID 不能为空");
            if (id == ROOT_ID || !ids.add(id)) {
                throw new IllegalArgumentException("树节点 ID 重复或占用根 ID: " + id);
            }
            Long parent = node.getParentId() == null ? ROOT_ID : node.getParentId();
            byParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
        }
        List<E> roots = List.copyOf(byParent.getOrDefault(ROOT_ID, List.of()));
        ArrayDeque<E> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            E node = pending.removeFirst();
            List<E> children = List.copyOf(byParent.getOrDefault(node.getId(), List.of()));
            node.setChildren(children);
            pending.addAll(children);
        }
        return roots;
    }

    /** 查询是否存在直接子项，保留旧查询回调返回 null 时视作无子项的约定。 */
    public static boolean hasChildren(long id, Function<Long, ? extends List<?>> finder) {
        List<?> children = finder.apply(id);
        return children != null && !children.isEmpty();
    }

    /**
     * 后代集合不包含起点，仍可由调用方追加起点。每个 ID 最多查询一次；
     * 脏数据成环或同一后代重复出现时终止扩展，不会挂住库存筛选请求。
     */
    public static <E extends TreeNode<E>> Set<Long> getChildrenIdList(long parentId, Function<Long, List<E>> finder) {
        Set<Long> result = new LinkedHashSet<>();
        Set<Long> visited = new HashSet<>();
        visited.add(parentId);
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(parentId);
        while (!pending.isEmpty()) {
            List<E> children = finder.apply(pending.removeFirst());
            if (children == null) {
                continue;
            }
            for (E child : children) {
                Long id = Objects.requireNonNull(child.getId(), "树节点 ID 不能为空");
                if (visited.add(id)) {
                    result.add(id);
                    pending.addLast(id);
                }
            }
        }
        return result;
    }
}
