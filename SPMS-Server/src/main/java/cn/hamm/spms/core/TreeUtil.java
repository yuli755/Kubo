package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.spms.core.interfaces.IEntity;
import cn.hamm.spms.core.interfaces.ITree;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Function;

/**
 * <h1>树结构处理工具类</h1>
 *
 * @author Hamm.cn
 */
public class TreeUtil {
    /**
     * 根节点 ID
     */
    public static final long ROOT_ID = 0L;

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private TreeUtil() {
    }

    /**
     * 生成树结构
     *
     * @param list 原始数据列表
     * @param <E>  泛型
     * @return 树结构数组
     */
    public static <E extends IEntity<E> & ITree<E>> @Unmodifiable @NotNull List<E> buildTreeList(List<E> list) {
        return buildTreeListOptimized(list, ROOT_ID);
    }

    /**
     * 生成树结构（优化版 - O(n) 复杂度）
     *
     * @param list     原始数据列表
     * @param parentId 父级 ID
     * @param <E>      泛型
     * @return 树结构数组
     * @apiNote 使用 Map 预构建 parentId -> children 映射，将时间复杂度从 O(n²) 优化到 O(n)
     */
    private static <E extends IEntity<E> & ITree<E>> @Unmodifiable @NotNull List<E> buildTreeListOptimized(@NotNull List<E> list, long parentId) {
        Map<Long, List<E>> parentMap = new HashMap<>();
        for (E item : list) {
            Long pid = item.getParentId() != null ? item.getParentId() : ROOT_ID;
            parentMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(item);
        }
        return buildTreeWithMap(parentMap, parentId);
    }

    /**
     * 使用 Map 构建树结构
     *
     * @param parentMap parentId -> children 映射
     * @param parentId  父级 ID
     * @param <E>       泛型
     * @return 树结构数组
     */
    private static <E extends IEntity<E> & ITree<E>> @UnmodifiableView @NotNull List<E> buildTreeWithMap(@NotNull Map<Long, List<E>> parentMap, long parentId) {
        List<E> children = parentMap.getOrDefault(parentId, Collections.emptyList());
        List<E> result = new ArrayList<>(children.size());
        for (E child : children) {
            result.add(child.setChildren(buildTreeWithMap(parentMap, child.getId())));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 根据父级 ID 获取所有子节点
     *
     * @param parentId 父级 ID
     * @return 子节点列表
     */
    public static <
            E extends IEntity<E> & ITree<E>
            > @NotNull List<E> findByParentId(@Nullable Long parentId, @NotNull Function<Long, List<E>> function) {
        if (Objects.isNull(parentId)) {
            parentId = ROOT_ID;
        }
        List<E> apply = function.apply(parentId);
        if (Objects.isNull(apply)) {
            return List.of();
        }
        return apply;
    }

    /**
     * 删除前确认是否包含子节点数据
     *
     * @param id       待删除的 ID
     * @param function 获取子节点的函数
     */
    public static <
            E extends IEntity<E> & ITree<E>
            > void ensureNoChildrenBeforeDelete(long id, @NotNull Function<Long, List<E>> function) {
        List<E> apply = function.apply(id);
        if (Objects.isNull(apply)) {
            return;
        }
        if (!apply.isEmpty()) {
            throw new ServiceException("无法删除含有下级的数据，请先删除所有下级！");
        }
    }

    /**
     * 获取指定父ID下的所有子 ID
     *
     * @param parentId 父 ID
     */
    public static <
            E extends IEntity<E> & ITree<E>
            > @NotNull Set<Long> getChildrenIdList(
            long parentId,
            @NotNull Function<Long, List<E>> function
    ) {
        Set<Long> list = new HashSet<>();
        List<E> children = function.apply(parentId);
        if (Objects.isNull(children)) {
            children = List.of();
        }
        children.stream().map(IEntity::getId).forEach(id -> {
            list.add(id);
            list.addAll(getChildrenIdList(id, function));
        });
        return list;
    }
}
