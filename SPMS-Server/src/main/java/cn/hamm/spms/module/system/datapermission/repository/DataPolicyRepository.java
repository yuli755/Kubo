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
    List<DataPolicyEntity> findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(Long resourceId, String actionName);

    /**
     * 按资源取全部启用中的行级策略（不分动作）
     *
     * <p>只为管理端"永不命中"盘点存在：热路径按 {@code 资源 + 动作} 精确取数，
     * 于是<b>动作名写错的那一行在任何请求里都不会被读到</b> —— 想抓它就必须绕过动作过滤。</p>
     */
    List<DataPolicyEntity> findByResourceIdAndEnabledTrueOrderByPriorityDesc(Long resourceId);
}
