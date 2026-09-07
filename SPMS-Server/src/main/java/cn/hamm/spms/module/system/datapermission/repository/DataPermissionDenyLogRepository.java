package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDenyLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h1>数据权限拒绝事件仓库</h1>
 *
 * <p>与 {@link DataPermissionDiffRepository} 同样的取舍：不继承 {@code BaseRepository} /
 * {@code ICurdRepository}，埋点表多一个 CRUD 端点就多一份越权面。只暴露写入与按资源/原因查询。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataPermissionDenyLogRepository extends JpaRepository<DataPermissionDenyLogEntity, Long> {

    /**
     * 某资源最近的拒绝事件（排障用）
     */
    List<DataPermissionDenyLogEntity> findTop50ByResourceKeyOrderByIdDesc(String resourceKey);

    /**
     * 全量最近的拒绝事件（管理端看板：放开后第一眼要看的「挡了谁」）
     */
    List<DataPermissionDenyLogEntity> findTop50ByOrderByIdDesc();

    /**
     * 删除指定时间点之前的拒绝事件；长期归档由数据库备份/归档层负责。
     *
     * <p>用原生 SQL 是刻意的：{@code create_time} 来自 AirPower 的基类，应用 Java 属性名不是本模块
     * 的契约；按物理列写可以让迁移脚本、归档任务与实体映射认同一件事。</p>
     *
     * @param cutoffEpochMillis 截止时间（该时刻之前的记录可删除）
     * @return 删除行数
     */
    @Modifying
    @Transactional
    @Query(value = "delete from data_permission_deny_log where create_time < :cutoff", nativeQuery = true)
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
