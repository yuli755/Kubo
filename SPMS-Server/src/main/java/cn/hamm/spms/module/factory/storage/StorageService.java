package cn.hamm.spms.module.factory.storage;

import cn.hamm.airpower.core.TreeUtil;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class StorageService extends BaseService<StorageEntity, StorageRepository> {
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    @Autowired
    private StorageHierarchyIndex hierarchyIndex;

    @Autowired
    private cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher eventPublisher;

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<StorageEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull StorageEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "factory.storage", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Override
    protected @NotNull List<StorageEntity> afterGetList(@NotNull List<StorageEntity> list) {
        list.forEach(item -> {
            QueryListRequest<StorageEntity> queryListRequest = new QueryListRequest<>();
            queryListRequest.setFilter(new StorageEntity().setParentId(item.getId()));
            item.setChildren(getList(queryListRequest));
        });
        return list;
    }

    @Override
    protected void beforeDelete(@NotNull StorageEntity storage) {
        TreeUtil.ensureNoChildrenBeforeDelete(storage.getId(), id -> filter(new StorageEntity().setParentId(id)));
        afterTreeChanged();
    }

    @Override
    protected void afterAppAdd(long id, @NotNull StorageEntity storage) {
        super.afterAppAdd(id, storage);
        afterTreeChanged();
    }

    @Override
    protected void afterUpdate(long id, @NotNull StorageEntity storage) {
        super.afterUpdate(id, storage);
        afterTreeChanged();
    }

    /**
     * 仓库/工厂树变了：打掉本进程的层级快照，并广播给其他节点（D13）
     *
     * <p>与 {@code DepartmentService#afterTreeChanged} 同构；Redis 不可用时广播只记 WARN，
     * 各节点靠 TTL（默认 300 秒）自愈。</p>
     */
    private void afterTreeChanged() {
        hierarchyIndex.invalidate();
        try {
            eventPublisher.publishStorageTreeChanged();
        } catch (RuntimeException e) {
            log.warn("仓库/工厂树变更广播失败（各节点将在 TTL 后自愈）: {}", e.getMessage());
        }
    }

    @Override
    protected @NotNull QueryListRequest<StorageEntity> beforeGetList(@NotNull QueryListRequest<StorageEntity> queryListRequest) {
        StorageEntity filter = queryListRequest.getFilter();
        if (Objects.isNull(filter.getParentId())) {
            filter.setParentId(TreeUtil.ROOT_ID);
        }
        queryListRequest.setFilter(filter);
        return queryListRequest;
    }
}
