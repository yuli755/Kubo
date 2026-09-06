package cn.hamm.spms.module.system.tenant;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>用户-租户数据源</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface UserTenantRepository extends BaseRepository<UserTenantEntity> {
    /**
     * 查询用户归属的全部租户关系
     *
     * @param userId 用户 ID
     * @return 关系列表
     */
    List<UserTenantEntity> findByUserId(Long userId);

    /**
     * 查询用户的默认租户关系
     *
     * @param userId 用户 ID
     * @return 默认关系，可能为 null
     */
    UserTenantEntity findByUserIdAndIsDefaultTrue(Long userId);
}
