package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
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
}
