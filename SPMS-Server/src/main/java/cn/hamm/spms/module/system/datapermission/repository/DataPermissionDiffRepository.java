package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.mapper.KuboInfraMapper;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>数据权限影子比对差异仓储</h1>
 *
 * <p>2026-09-18 由 Spring Data JPA 换成 MyBatis-Plus（去 JPA 第一批，理由与写法见
 * {@link DataPermissionDenyLogRepository} 的说明）。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataPermissionDiffRepository extends KuboInfraMapper<DataPermissionDiffEntity> {
    /**
     * 某资源最近 50 条差异记录（倒序）
     *
     * @param resourceKey 资源标识
     * @return 差异列表
     */
    default List<DataPermissionDiffEntity> findTop50ByResourceKeyOrderByIdDesc(String resourceKey) {
        return selectList(new LambdaQueryWrapper<DataPermissionDiffEntity>()
                .eq(DataPermissionDiffEntity::getResourceKey, resourceKey)
                .orderByDesc(DataPermissionDiffEntity::getId)
                .last("LIMIT 50"));
    }

    /**
     * 某资源某结论的条数
     *
     * @param resourceKey 资源标识
     * @param verdict     结论（CONSISTENT / ANOMALY / ERROR …）
     * @return 条数
     */
    default long countByResourceKeyAndVerdict(String resourceKey, String verdict) {
        Long count = selectCount(new LambdaQueryWrapper<DataPermissionDiffEntity>()
                .eq(DataPermissionDiffEntity::getResourceKey, resourceKey)
                .eq(DataPermissionDiffEntity::getVerdict, verdict));
        return count == null ? 0L : count;
    }

    /**
     * 删除截止线之前的差异记录（保留期清理）
     *
     * @param cutoffEpochMillis 截止时间（毫秒）
     * @return 删除行数
     */
    @Delete("delete from data_permission_diff_log where create_time < #{cutoff}")
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
