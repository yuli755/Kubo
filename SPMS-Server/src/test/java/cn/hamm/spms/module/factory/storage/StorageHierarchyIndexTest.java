package cn.hamm.spms.module.factory.storage;

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
 * 仓库/工厂树索引测试（D13）
 *
 * <p>与 {@code DepartmentHierarchyIndexTest} 同一套判据：① 判定路径不查库；② 失效后能重建；
 * ③ 读库失败时退化成「不展开」，既不抛也不放开。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("StorageHierarchyIndex：进程内快照")
class StorageHierarchyIndexTest {

    private StorageRepository repository;

    private DataPermissionProperties properties;

    private StorageHierarchyIndex index;

    /**
     * 造一棵 1（工厂）-> (2 -> 4 -> 5) / 3 的树
     */
    private static List<StorageEntity> sampleTree() {
        List<StorageEntity> list = new ArrayList<>();
        list.add(storage(1L, 0L));
        list.add(storage(2L, 1L));
        list.add(storage(3L, 1L));
        list.add(storage(4L, 2L));
        list.add(storage(5L, 4L));
        return list;
    }

    private static StorageEntity storage(Long id, Long parentId) {
        StorageEntity entity = new StorageEntity();
        entity.setId(id);
        entity.setParentId(parentId);
        return entity;
    }

    @BeforeEach
    void setUp() {
        repository = mock(StorageRepository.class);
        properties = new DataPermissionProperties();
        index = new StorageHierarchyIndex(repository, properties);
    }

    @Test
    @DisplayName("展开包含全部后代，且同一 TTL 内只读一次全表")
    void expandsFromSnapshotAndReadsOnce() {
        doReturn(sampleTree()).when(repository).findAll();

        HierarchyClosure.Closure first = index.expandFrom(Set.of(1L));
        HierarchyClosure.Closure second = index.expandFrom(Set.of(2L));

        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), first.ids());
        assertEquals(Set.of(2L, 4L, 5L), second.ids());
        verify(repository, times(1)).findAll();
    }

    @Test
    @DisplayName("invalidate 之后重建：树改完立刻生效，不用等 TTL")
    void invalidateForcesRebuild() {
        doReturn(sampleTree()).when(repository).findAll();
        assertTrue(index.expandFrom(Set.of(1L)).ids().contains(5L));

        index.invalidate();
        doReturn(List.of(storage(1L, 0L), storage(2L, 1L))).when(repository).findAll();

        Set<Long> ids = index.expandFrom(Set.of(1L)).ids();
        assertEquals(Set.of(1L, 2L), ids);
        verify(repository, times(2)).findAll();
    }

    @Test
    @DisplayName("TTL 置 0 = 每次判定都重读")
    void zeroTtlDisablesCaching() {
        doReturn(sampleTree()).when(repository).findAll();
        properties.getHierarchy().setTtlSeconds(0L);

        index.expandFrom(Set.of(1L));
        index.expandFrom(Set.of(1L));

        verify(repository, times(2)).findAll();
    }

    @Test
    @DisplayName("读库失败：沿用不展开的语义，不抛异常也不放开")
    void degradesToSeedsOnlyWhenLoadFails() {
        doThrow(new IllegalStateException("db down")).when(repository).findAll();

        HierarchyClosure.Closure closure = index.expandFrom(Set.of(1L, 2L));

        assertEquals(Set.of(1L, 2L), closure.ids());
        assertFalse(closure.truncated());
        assertFalse(index.isUsable());
    }

    @Test
    @DisplayName("空种子：一次都不查库")
    void emptySeedsNeverTouchRepository() {
        HierarchyClosure.Closure closure = index.expandFrom(Set.of());

        assertEquals(Set.of(), closure.ids());
        verify(repository, times(0)).findAll();
    }
}
