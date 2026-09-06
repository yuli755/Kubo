package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>数据字段权限仓库</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataFieldPolicyRepository extends BaseRepository<DataFieldPolicyEntity> {
    List<DataFieldPolicyEntity> findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(Long resourceId, String actionName);

    /**
     * 按资源取全部启用策略（不分动作）
     *
     * <p>只为管理端的"没人读的策略"盘点存在：盘点要抓的正是<b>动作名不在消费集里</b>的行，
     * 按动作逐个查（6 个枚举值 × 每个资源）既慢又抓不到写错的动作名（如 `REPROT`）
     * —— 而写错动作恰恰是最该被发现的。</p>
     */
    List<DataFieldPolicyEntity> findByResourceIdAndEnabledTrueOrderByPriorityDesc(Long resourceId);

    /**
     * 根据资源键、字段名、角色编码查询启用的策略（用于前端动态获取脱敏规则）
     */
    @Query("SELECT dfp FROM DataFieldPolicyEntity dfp " +
           "JOIN dfp.resource dr " +
           "WHERE dr.resourceKey = :resourceKey " +
           "AND dfp.fieldName = :fieldName " +
           "AND dfp.roleCode = :roleCode " +
           "AND dfp.enabled = true")
    List<DataFieldPolicyEntity> findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue(
            @Param("resourceKey") String resourceKey,
            @Param("fieldName") String fieldName,
            @Param("roleCode") String roleCode);
}
