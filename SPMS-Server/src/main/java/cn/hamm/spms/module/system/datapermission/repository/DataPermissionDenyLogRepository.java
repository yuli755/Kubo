package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.mapper.KuboInfraMapper;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDenyLogEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>数据权限拒绝事件仓储</h1>
 *
 * <p>2026-09-18 由 Spring Data JPA 换成 MyBatis-Plus（去 JPA 的第一批：这一族证据表**不依赖 CRUD 引擎**，
 * 可以整块先换）。三处变化，都是"JPA 方言 → MyBatis-Plus 方言"，语义逐条对齐：</p>
 *
 * <ul>
 *   <li>{@code extends JpaRepository<..., Long>} → {@code extends KuboInfraMapper<...>}
 *       （见 {@link KuboInfraMapper}：无条件注册，不跟读侧灰度开关走）；</li>
 *   <li>派生查询 {@code findTop50ByResourceKeyOrderByIdDesc} → {@code default} 方法 + {@code LambdaQueryWrapper}
 *       （用方法引用写列名，编译期就能发现字段改名；{@code last("LIMIT 50")} 对应派生查询里的 {@code Top50}）；</li>
 *   <li>原生 {@code @Query(value = "delete ... where create_time < :cutoff", nativeQuery = true)} →
 *       MyBatis 的 {@code @Delete}，**刻意保留显式 SQL**：证据表的保留期清理是审计对象，
 *       表名与条件应该明明白白写在仓储里（契约测试 `maskContract.spec.ts` 也钉着这句话）；
 *       占位符从 JPA 的 {@code :cutoff} 换成 MyBatis 的 {@code #{cutoff}}，其余一字不变。</li>
 * </ul>
 *
 * <p><b>调用点需要注意的一处差异</b>：JPA 的 {@code save(entity)} 已被 MyBatis-Plus 的
 * {@code insert(entity)} 取代（写入方 {@code DataPermissionDenyLogRecorder} 同批改）。
 * 这是**唯一**需要改调用方的语义变化 —— 其余方法名与签名保持不变。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataPermissionDenyLogRepository extends KuboInfraMapper<DataPermissionDenyLogEntity> {
    /**
     * 某资源最近 50 条拒绝事件（倒序）
     *
     * @param resourceKey 资源标识
     * @return 事件列表
     */
    default List<DataPermissionDenyLogEntity> findTop50ByResourceKeyOrderByIdDesc(String resourceKey) {
        return selectList(new LambdaQueryWrapper<DataPermissionDenyLogEntity>()
                .eq(DataPermissionDenyLogEntity::getResourceKey, resourceKey)
                .orderByDesc(DataPermissionDenyLogEntity::getId)
                .last("LIMIT 50"));
    }

    /**
     * 最近 50 条拒绝事件（倒序）
     *
     * @return 事件列表
     */
    default List<DataPermissionDenyLogEntity> findTop50ByOrderByIdDesc() {
        return selectList(new LambdaQueryWrapper<DataPermissionDenyLogEntity>()
                .orderByDesc(DataPermissionDenyLogEntity::getId)
                .last("LIMIT 50"));
    }

    /**
     * 删除截止线之前的拒绝事件（保留期清理）
     *
     * @param cutoffEpochMillis 截止时间（毫秒）
     * @return 删除行数
     */
    @Delete("delete from data_permission_deny_log where create_time < #{cutoff}")
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
