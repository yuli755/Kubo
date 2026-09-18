package cn.hamm.spms.module.system.tenant;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

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
    /**
     * 查询用户的全部租户关系
     *
     * @param userId 用户 ID
     * @return 租户关系列表
     * @apiNote 派生查询名 {@code findByUserId} → wrapper 的 {@code eq("user_id", …)}
     */
    default List<UserTenantEntity> findByUserId(Long userId) {
        return selectList(new QueryWrapper<UserTenantEntity>().eq("user_id", userId));
    }

    /**
     * 查询用户的默认租户关系
     *
     * @param userId 用户 ID
     * @return 默认关系，可能为 null
     */
    /**
     * 查询用户的默认租户
     *
     * @param userId 用户 ID
     * @return 租户关系（没有默认租户时返回 {@code null}）
     * @apiNote 派生名里的 {@code IsDefaultTrue} 是 {@code is_default = true} 这个条件 —— 逐字保留
     */
    default UserTenantEntity findByUserIdAndIsDefaultTrue(Long userId) {
        return selectOne(new QueryWrapper<UserTenantEntity>().eq("user_id", userId).eq("is_default", true));
    }
}
