package cn.hamm.spms.module.personnel.user.token;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>数据库连接信息</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface PersonalTokenRepository extends BaseRepository<PersonalTokenEntity> {
    /**
     * 通过令牌查询
     *
     * @param token 令牌
     * @return 应用
     */
    /**
     * 按令牌查询
     *
     * @param token 令牌
     * @return 令牌记录
     */
    default PersonalTokenEntity getByToken(String token) {
        return selectOne(new QueryWrapper<PersonalTokenEntity>().eq("token", token));
    }
}
