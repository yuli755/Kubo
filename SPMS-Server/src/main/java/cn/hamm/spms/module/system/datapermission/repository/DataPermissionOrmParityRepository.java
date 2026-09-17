package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.mapper.KuboInfraMapper;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>数据权限 ORM 行数对账仓储</h1>
 *
 * <p>2026-09-18 由 Spring Data JPA 换成 MyBatis-Plus（去 JPA 第一批，写法见
 * {@link DataPermissionDenyLogRepository} 的说明）。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataPermissionOrmParityRepository extends KuboInfraMapper<DataPermissionOrmParityEntity> {
    /**
     * 某资源最近 50 条对账记录（倒序）
     *
     * @param resourceKey 资源标识
     * @return 对账列表
     */
    default List<DataPermissionOrmParityEntity> findTop50ByResourceKeyOrderByIdDesc(String resourceKey) {
        return selectList(new LambdaQueryWrapper<DataPermissionOrmParityEntity>()
                .eq(DataPermissionOrmParityEntity::getResourceKey, resourceKey)
                .orderByDesc(DataPermissionOrmParityEntity::getId)
                .last("LIMIT 50"));
    }

    /**
     * 某资源某结论的条数
     *
     * @param resourceKey 资源标识
     * @param verdict     结论（CONSISTENT / MAPPER_STRICT / MAPPER_LOOSE / ERROR）
     * @return 条数
     */
    default long countByResourceKeyAndVerdict(String resourceKey, String verdict) {
        Long count = selectCount(new LambdaQueryWrapper<DataPermissionOrmParityEntity>()
                .eq(DataPermissionOrmParityEntity::getResourceKey, resourceKey)
                .eq(DataPermissionOrmParityEntity::getVerdict, verdict));
        return count == null ? 0L : count;
    }

    /**
     * 删除截止线之前的对账记录（保留期清理）
     *
     * <p>JPA 版本在方法上挂了 {@code @Modifying @Transactional}；MyBatis-Plus 的 Mapper 直接执行 SQL，
     * 无事务时按语句自动提交，因此这里**刻意不补** {@code @Transactional} —— 保留
     * "三张证据表各自独立清理、某一张失败不影响其它两张"的既有行为
     * （调用方 {@code DataPermissionEvidenceRetention} 逐个 try/catch）。</p>
     *
     * @param cutoffEpochMillis 截止时间（毫秒）
     * @return 删除行数
     */
    @Delete("delete from data_permission_orm_parity_log where create_time < #{cutoff}")
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
