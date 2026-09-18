package cn.hamm.spms.module.system.tenant;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

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
    /**
     * 按编码查询租户
     *
     * @param code 编码
     * @return 租户
     */
    default TenantEntity getByCode(String code) {
        return selectOne(new QueryWrapper<TenantEntity>().eq("code", code));
    }
}
