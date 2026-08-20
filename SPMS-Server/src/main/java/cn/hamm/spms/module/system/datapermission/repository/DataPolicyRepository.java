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
}
