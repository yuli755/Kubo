package cn.hamm.spms.module.personnel.department;

import cn.hamm.spms.common.tree.HierarchyClosure;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 部门树索引测试
 *
 * <p>要钉住三件事：① 判定路径<b>不查库</b>（快照命中时一次都不读）；② 失效后能重建；
 * ③ 读库失败时<b>退化成"不展开"</b>而不是抛、也不是"放开"。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DepartmentHierarchyIndex：进程内快照")
class DepartmentHierarchyIndexTest {

    private DepartmentRepository repository;

    private DataPermissionProperties properties;

    private DepartmentHierarchyIndex index;

    /**
     * 造一棵 1 -> (2 -> 4 -> 5) / 3 的树
     */
    private static List<DepartmentEntity> sampleTree() {
        List<DepartmentEntity> list = new ArrayList<>();
        list.add(department(1L, 0L));
        list.add(department(2L, 1L));
        list.add(department(3L, 1L));
        list.add(department(4L, 2L));
        list.add(department(5L, 4L));
        return list;
    }

    private static DepartmentEntity department(Long id, Long parentId) {
        DepartmentEntity entity = new DepartmentEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        return entity;
    }

    @BeforeEach
    void setUp() {
        repository = mock(DepartmentRepository.class);
        properties = new DataPermissionProperties();
        index = new DepartmentHierarchyIndex(repository, properties);
    }

    @Test
    @DisplayName("展开包含全部后代，且同一 TTL 内只读一次全表")
    void expandsFromSnapshotAndReadsOnce() {
        doReturn(sampleTree()).when(repository).selectList(null);

        HierarchyClosure.Closure first = index.expandFrom(Set.of(1L));
        HierarchyClosure.Closure second = index.expandFrom(Set.of(2L));

        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), first.ids());
        assertEquals(Set.of(2L, 4L, 5L), second.ids());
        verify(repository, times(1)).selectList(null);
    }

    @Test
    @DisplayName("invalidate 之后重建：部门改完立刻生效，不用等 TTL")
    void invalidateForcesRebuild() {
        doReturn(sampleTree()).when(repository).selectList(null);
        assertTrue(index.expandFrom(Set.of(1L)).ids().contains(5L));

        index.invalidate();
        doReturn(List.of(department(1L, 0L), department(2L, 1L))).when(repository).selectList(null);

        Set<Long> ids = index.expandFrom(Set.of(1L)).ids();
        assertEquals(Set.of(1L, 2L), ids);
        verify(repository, times(2)).selectList(null);
    }

    @Test
    @DisplayName("TTL 置 0 = 每次判定都重读（留给出问题现场用的开关）")
    void zeroTtlDisablesCaching() {
        doReturn(sampleTree()).when(repository).selectList(null);
        properties.getHierarchy().setTtlSeconds(0L);

        index.expandFrom(Set.of(1L));
        index.expandFrom(Set.of(1L));

        verify(repository, times(2)).selectList(null);
    }

    @Test
    @DisplayName("读库失败：沿用不展开的语义，不抛异常也不放开")
    void degradesToSeedsOnlyWhenLoadFails() {
        doThrow(new IllegalStateException("db down")).when(repository).selectList(null);

        HierarchyClosure.Closure closure = index.expandFrom(Set.of(1L, 2L));

        assertEquals(Set.of(1L, 2L), closure.ids());
        assertFalse(closure.truncated());
        assertFalse(index.isUsable());
    }

    @Test
    @DisplayName("失效后读库失败：暂时按「不展开」处理，恢复后立刻正常（既不抛也不放开）")
    void failsSoftThenRecovers() {
        doReturn(sampleTree()).when(repository).selectList(null);
        // 先把快照建起来：isUsable 在第一次真正展开之前一直是 false（懒构建）
        assertTrue(index.expandFrom(Set.of(1L)).ids().contains(5L));
        assertTrue(index.isUsable());

        index.invalidate();
        // invalidate 会丢掉旧快照（把 builtAt 推成哨兵会在 nanoTime 减法上溢出），
        // 所以这里失败就是「暂时不展开」，下一次成功再恢复 —— 关键是它既不抛也不放开
        //
        // 桩一律用 doReturn/doThrow 而不是 when(mock.x()).thenReturn(...)：后者会**真的调用一次**
        // mock 来登记调用，若上一个桩是 thenThrow，这一行自己就把异常抛出来了（CI 实测：
        // 「恢复」那一步在 when(...) 处炸，与被测代码毫无关系）。
        doThrow(new IllegalStateException("db down")).when(repository).selectList(null);
        assertEquals(Set.of(1L), index.expandFrom(Set.of(1L)).ids());

        doReturn(sampleTree()).when(repository).selectList(null);
        assertTrue(index.expandFrom(Set.of(1L)).ids().contains(5L));
    }

    @Test
    @DisplayName("descendantsOf 不含种子且返回可变集合（调用方要往里 add 自己）")
    void descendantsExcludeSeedAndAreMutable() {
        doReturn(sampleTree()).when(repository).selectList(null);

        Set<Long> descendants = index.descendantsOf(1L);

        assertEquals(Set.of(2L, 3L, 4L, 5L), descendants);
        assertTrue(descendants.add(1L));
    }

    @Test
    @DisplayName("脏数据成环：不 StackOverflow，也不把同一节点算两次")
    void survivesCycles() {
        doReturn(List.of(department(1L, 3L), department(2L, 1L), department(3L, 2L))).when(repository).selectList(null);

        Set<Long> ids = index.expandFrom(Set.of(1L)).ids();

        assertEquals(Set.of(1L, 2L, 3L), ids);
    }

    @Test
    @DisplayName("空部门表：展开结果只剩种子（等价于没开层级）")
    void emptyTable() {
        doReturn(List.of()).when(repository).selectList(null);

        assertEquals(Set.of(9L), index.expandFrom(Set.of(9L)).ids());
        assertTrue(index.descendantsOf(9L).isEmpty());
    }

    @Test
    @DisplayName("种子为空：连全表都不读（多数用户没有部门，这是每个请求都会走的热路径）")
    void emptySeedsShortCircuits() {
        assertTrue(index.expandFrom(Set.of()).ids().isEmpty());
        assertTrue(index.expandFrom(null).ids().isEmpty());

        verify(repository, times(0)).selectList(null);
    }
}
