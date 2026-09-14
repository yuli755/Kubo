package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>数据权限策略仓库</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataPolicyRepository extends BaseRepository<DataPolicyEntity> {
    /**
     * 判断某资源某动作是否已登记指定主体的策略。
     *
     * <p>存在的理由：用实体当查询条件的 {@code filter(new DataPolicyEntity()....)} 会把实体
     * <b>字段默认值</b>（versionNo/priority/enabled/conditionJson）一并拼进 WHERE，
     * 初始化守卫因此永远查不到已存在的行，重跑即重复插入。</p>
     */
    boolean existsByResourceIdAndActionNameAndSubjectType(Long resourceId, String actionName, String subjectType);

    List<DataPolicyEntity> findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(Long resourceId, String actionName);

    /**
     * 按资源取全部启用中的行级策略（不分动作）
     *
     * <p>只为管理端"永不命中"盘点存在：热路径按 {@code 资源 + 动作} 精确取数，
     * 于是<b>动作名写错的那一行在任何请求里都不会被读到</b> —— 想抓它就必须绕过动作过滤。</p>
     */
    List<DataPolicyEntity> findByResourceIdAndEnabledTrueOrderByPriorityDesc(Long resourceId);

    /**
     * 全部启用中的行级策略（跨资源）
     *
     * <p>只为「策略指向缺失资源」盘点存在：那类风险恰恰是资源已经不在了 / 未启用，按资源遍历
     * 会把它们漏掉 —— 必须反过来从策略出发，才能看见「引用坏掉」的行。</p>
     */
    List<DataPolicyEntity> findByEnabledTrueOrderByPriorityDesc();
}
