package cn.hamm.spms.module.system.config;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>数据库连接信息</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface ConfigRepository extends BaseRepository<ConfigEntity> {
    /**
     * 根据标识查询配置信息
     *
     * @param flag 配置标识
     * @return 配置信息
     */
    /**
     * 按标识查询配置
     *
     * @param flag 标识
     * @return 配置
     */
    default ConfigEntity getByFlag(String flag) {
        return selectOne(new QueryWrapper<ConfigEntity>().eq("flag", flag));
    }
}
