package cn.hamm.spms.module.personnel.department;

import cn.hamm.spms.core.TreeUtil;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN_EXIST;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class DepartmentService extends BaseService<DepartmentEntity, DepartmentRepository> {
    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    @Autowired
    private DepartmentHierarchyIndex hierarchyIndex;

    @Autowired
    private cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher eventPublisher;

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<DepartmentEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull DepartmentEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "personnel.department", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    /**
     * 排序字段
     */
    static final String ORDER_FIELD_NAME = "orderNo";

    @Override
    protected void beforeDelete(@NotNull DepartmentEntity department) {
        TreeUtil.ensureNoChildrenBeforeDelete(department.getId(), (id) -> filter(new DepartmentEntity().setId(id)));
        afterTreeChanged();
    }

    @Override
    protected void afterAppAdd(long id, @NotNull DepartmentEntity department) {
        super.afterAppAdd(id, department);
        afterTreeChanged();
    }

    @Override
    protected void afterUpdate(long id, @NotNull DepartmentEntity department) {
        super.afterUpdate(id, department);
        afterTreeChanged();
    }

    /**
     * 树变了：打掉本进程的层级快照，并广播给其他节点
     *
     * <p>删除路径在 {@link #beforeDelete} 里失效时节点其实还在库里，重建出来的快照可能仍含该节点 ——
     * 这是可接受的：那里先有 {@code TreeUtil.ensureNoChildrenBeforeDelete} 挡掉"有子节点的删除"，
     * 所以最多是多带一个已不存在的叶子 ID，查不出数据，也不会把范围放宽到别的部门。
     * Redis 不可用时广播只记 WARN，各节点靠 TTL（默认 300 秒）自愈。</p>
     */
    private void afterTreeChanged() {
        hierarchyIndex.invalidate();
        try {
            eventPublisher.publishDepartmentTreeChanged();
        } catch (RuntimeException e) {
            log.warn("部门树变更广播失败（各节点将在 TTL 后自愈）: {}", e.getMessage());
        }
    }

    @Override
    protected @NotNull List<DepartmentEntity> afterGetList(@NotNull List<DepartmentEntity> list) {
        list.forEach(DepartmentEntity::excludeNotMeta);
        return list;
    }

    @Override
    protected DepartmentEntity beforeAppSaveToDatabase(@NotNull DepartmentEntity department) {
        DepartmentEntity filter = new DepartmentEntity().setParentId(department.getParentId()).setName(department.getName());
        List<DepartmentEntity> exists = filter(filter);
        if (Objects.nonNull(department.getId())) {
            // 有ID 编辑 不允许有同名的部门 且ID不是自己的部门
            FORBIDDEN_EXIST.when(!exists.isEmpty() && !Objects.equals(exists.get(0).getId(), department.getId()), "同级别下部门不允许重复");
        } else {
            FORBIDDEN_EXIST.when(!exists.isEmpty(), "同级别下部门已有同名部门");
        }
        return department;
    }

    /**
     * 获取指定部门的<b>全部后代</b>部门 ID（不含自身）
     *
     * <p>这里原本是一段"逐层查库 + 递归"的实现，而且<b>从来没把子节点加进结果集</b>：
     * 递归体只调了 {@code getListByParentId(child.getId(), departmentIds)} 就丢掉返回值，
     * 于是返回恒为空集（{@code @Contract("_, _ -> param2")} 还把这个行为写成了规格）。
     * 后果落在调用方：{@code UserService} 按部门筛用户时只补了根节点自己，
     * <b>子部门下的人在用户列表里整列查不出来</b>。</p>
     *
     * <p>现在委托 {@link DepartmentHierarchyIndex}：一次读全表 + 内存 BFS，
     * 顺带拿到环保护与深度上限。返回值仍是<b>可变</b>集合 —— 调用方会往里 add 根节点自己。</p>
     *
     * @param parentId 父级ID
     * @return 子部门列表（可变、不含自身）
     */
    @Contract("_ -> new")
    public @NotNull Set<Long> getListByParentId(long parentId) {
        return hierarchyIndex.descendantsOf(parentId);
    }
}
