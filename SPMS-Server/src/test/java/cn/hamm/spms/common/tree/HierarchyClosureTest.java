package cn.hamm.spms.common.tree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 层级闭包展开测试
 *
 * <p>{@code parentId} 是业务可编辑的普通列，成环在真实数据里是可能发生的（把 A 挂到 A 的孙子下）。
 * 本类的重点因此不是"能不能遍历"，而是<b>脏数据不会把请求打挂、也不会静默少给</b>。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("HierarchyClosure：部门/仓库树闭包展开")
class HierarchyClosureTest {

    /**
     * 造 {@code id -> parentId} 映射
     */
    private static Map<Long, Long> parents(Object... idParentPairs) {
        Map<Long, Long> map = new HashMap<>();
        for (int i = 0; i < idParentPairs.length; i += 2) {
            map.put(((Number) idParentPairs[i]).longValue(),
                    idParentPairs[i + 1] == null ? null : ((Number) idParentPairs[i + 1]).longValue());
        }
        return map;
    }

    @Nested
    @DisplayName("正常树")
    class NormalTree {

        @Test
        @DisplayName("多层展开包含全部后代，顺序稳定")
        void expandsAllLevels() {
            // 1 -> (2, 3)，2 -> (4)，4 -> (5)
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(1L, 0L, 2L, 1L, 3L, 1L, 4L, 2L, 5L, 4L));

            HierarchyClosure.Closure closure = HierarchyClosure.expand(Set.of(1L), children);

            assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), closure.ids());
            assertFalse(closure.truncated());
            // 结果按层（BFS）稳定排序：日志与断言都靠这个顺序，别"哪天顺手换成 HashSet"
            assertEquals(java.util.List.of(1L, 2L, 3L, 4L, 5L), new java.util.ArrayList<>(closure.ids()));
        }

        @Test
        @DisplayName("中间节点作为种子时只向下展开，不含父级")
        void expandsFromMiddleNodeOnlyDownwards() {
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(1L, 0L, 2L, 1L, 3L, 1L, 4L, 2L));

            assertEquals(Set.of(2L, 4L), HierarchyClosure.expand(Set.of(2L), children).ids());
        }

        @Test
        @DisplayName("多个种子（用户挂多个部门）并集展开，重复的只算一次")
        void expandsMultipleSeeds() {
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(1L, 0L, 2L, 1L, 3L, 0L, 4L, 3L, 5L, 1L));

            HierarchyClosure.Closure closure = HierarchyClosure.expand(Set.of(1L, 3L), children);

            assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), closure.ids());
        }

        @Test
        @DisplayName("idsWithout 只留后代，且不改动原闭包")
        void idsWithoutDropsSeeds() {
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(1L, 0L, 2L, 1L, 3L, 1L));

            HierarchyClosure.Closure closure = HierarchyClosure.expand(Set.of(1L), children);

            assertEquals(Set.of(2L, 3L), closure.idsWithout(Set.of(1L)));
            // 原闭包不能被返回值改动牵连（快照是多个请求共享的）
            assertThrows(UnsupportedOperationException.class, () -> closure.ids().add(99L));
            // UserService 那一类调用方会往返回值里 add 根节点：给个不可变集就是当场 UnsupportedOperationException
            Set<Long> mutable = closure.idsWithout(Set.of(1L));
            assertTrue(mutable.add(99L));
            assertEquals(Set.of(1L, 2L, 3L), closure.ids());
        }
    }

    @Nested
    @DisplayName("脏数据与兜底")
    class DirtyData {

        @Test
        @DisplayName("成环不死循环：visited 让每个节点只走一次")
        void cycleTerminates() {
            // 1 -> 2 -> 3 -> 1（运营把 1 挂到了自己的孙子下面）
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(1L, 3L, 2L, 1L, 3L, 2L));

            HierarchyClosure.Closure closure = HierarchyClosure.expand(Set.of(1L), children);

            assertEquals(Set.of(1L, 2L, 3L), closure.ids());
            assertFalse(closure.truncated());
        }

        @Test
        @DisplayName("自环不会让节点无限套自己")
        void selfLoopIgnored() {
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(7L, 7L));

            assertEquals(Set.of(7L), HierarchyClosure.expand(Set.of(7L), children).ids());
            // 自环按根处理，父桶里不该出现 7 的子孙
            assertFalse(children.getOrDefault(7L, Set.of()).contains(7L));
        }

        @Test
        @DisplayName("超过深度上限：停止展开并标记 truncated（不抛）")
        void truncatesAtMaxDepth() {
            // 造一条 200 层的链
            Map<Long, Long> chain = new HashMap<>();
            for (long id = 1L; id <= 200L; id++) {
                chain.put(id, id - 1);
            }
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(chain);

            HierarchyClosure.Closure closure = HierarchyClosure.expand(Set.of(1L), children, 10);

            assertTrue(closure.truncated());
            assertEquals(11, closure.ids().size());
        }

        @Test
        @DisplayName("空输入：不抛，返回空闭包")
        void emptyInputs() {
            assertTrue(HierarchyClosure.expand(Set.of(), Map.of()).ids().isEmpty());
            assertTrue(HierarchyClosure.expand(null, Map.of()).ids().isEmpty());
            assertTrue(HierarchyClosure.childrenIndex(null).isEmpty());
            assertTrue(HierarchyClosure.childrenIndex(Map.of()).isEmpty());
        }

        @Test
        @DisplayName("parentId 为 null / 0 的按根处理，不会进任何父桶")
        void rootsHandled() {
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(1L, null, 2L, 0L));

            assertEquals(2, children.get(HierarchyClosure.ROOT).size());
            assertFalse(children.containsKey(1L));
            assertFalse(children.containsKey(2L));
        }
    }

    @Nested
    @DisplayName("快照不可变")
    class Immutable {

        @Test
        @DisplayName("邻接表与每个子集合都不可改：它是被多个请求共享的快照")
        void indexIsImmutable() {
            Map<Long, Set<Long>> children = HierarchyClosure.childrenIndex(parents(1L, 0L, 2L, 1L));

            assertThrows(UnsupportedOperationException.class, () -> children.put(9L, Set.of(8L)));
            assertThrows(UnsupportedOperationException.class, () -> children.get(HierarchyClosure.ROOT).add(9L));
        }
    }
}
