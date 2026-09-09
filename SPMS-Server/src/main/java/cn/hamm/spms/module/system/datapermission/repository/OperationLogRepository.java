package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.module.system.datapermission.entity.OperationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h1>通用操作审计日志仓库</h1>
 *
 * <p>与 {@link DataPermissionDenyLogRepository} 同一取舍：不继承 {@code BaseRepository} /
 * {@code ICurdRepository}——审计表多一个 CRUD 端点就多一份越权面。只暴露查询与清理。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {

    /**
     * 全量最近的操作日志（管理端倒序看板）
     */
    List<OperationLogEntity> findTop200ByOrderByIdDesc();

    /**
     * 某资源最近的操作日志（按资源下钻）
     */
    List<OperationLogEntity> findTop200ByResourceKeyOrderByIdDesc(String resourceKey);

    /**
     * 删除指定时间点之前的日志；长期归档由数据库备份/归档层负责。
     *
     * <p>用原生 SQL 是刻意的：{@code create_time} 来自 AirPower 基类，按物理列写让迁移脚本、
     * 归档任务与实体映射认同一件事。</p>
     *
     * @param cutoffEpochMillis 截止时间（该时刻之前的记录可删除）
     * @return 删除行数
     */
    @Modifying
    @Transactional
    @Query(value = "delete from operation_log where create_time < :cutoff", nativeQuery = true)
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
