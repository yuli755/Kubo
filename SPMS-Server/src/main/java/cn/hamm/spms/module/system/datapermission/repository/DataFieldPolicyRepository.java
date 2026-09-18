package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>数据字段权限仓库</h1>
 *
 * <p><b>带资源 join 的那条查询不在这里</b>：原来它是本接口上的 JPA {@code @Query}（JPQL 对象关联 JOIN），
 * 2026-09-18 按 SQL 分层规则搬进 {@code mapper/system/datapermission/DataFieldPolicyQueryMapper.xml}
 * —— 脱敏策略是权限判定链的一部分，那条 Query Mapper 属于无条件注册族。本接口剩下的都是派生查询。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataFieldPolicyRepository extends BaseRepository<DataFieldPolicyEntity> {
    /**
     * 判断某资源某字段是否已登记脱敏策略（同 {@code DataPolicyRepository#existsBy...} 的理由：
     * 实体默认值会污染 filter 查询条件，初始化守卫必须走显式存在性查询）
     */
    default boolean existsByResourceIdAndFieldName(Long resourceId, String fieldName) {
        Long count = selectCount(new QueryWrapper<DataFieldPolicyEntity>()
                .eq("resource_id", resourceId)
                .eq("field_name", fieldName));
        return count != null && count > 0;
    }

    default List<DataFieldPolicyEntity> findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(Long resourceId, String actionName) {
        return selectList(new QueryWrapper<DataFieldPolicyEntity>()
                .eq("resource_id", resourceId)
                .eq("action_name", actionName)
                .eq("enabled", true)
                .orderByDesc("priority"));
    }

    /**
     * 按资源取全部启用策略（不分动作）
     *
     * <p>只为管理端的"没人读的策略"盘点存在：盘点要抓的正是<b>动作名不在消费集里</b>的行，
     * 按动作逐个查（6 个枚举值 × 每个资源）既慢又抓不到写错的动作名（如 `REPROT`）
     * —— 而写错动作恰恰是最该被发现的。</p>
     */
    default List<DataFieldPolicyEntity> findByResourceIdAndEnabledTrueOrderByPriorityDesc(Long resourceId) {
        return selectList(new QueryWrapper<DataFieldPolicyEntity>()
                .eq("resource_id", resourceId)
                .eq("enabled", true)
                .orderByDesc("priority"));
    }


