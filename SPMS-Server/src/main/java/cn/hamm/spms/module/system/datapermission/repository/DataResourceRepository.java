package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import org.springframework.stereotype.Repository;

/**
 * <h1>数据资源仓库</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataResourceRepository extends BaseRepository<DataResourceEntity> {
    DataResourceEntity getByResourceKey(String resourceKey);
}
