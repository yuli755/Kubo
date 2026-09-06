package cn.hamm.spms.module.system.tenant;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;

/**
 * <h1>租户数据源</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface TenantRepository extends BaseRepository<TenantEntity> {
    /**
     * 按编码查询租户
     *
     * @param code 租户编码
     * @return 租户
     */
    TenantEntity getByCode(String code);
}
