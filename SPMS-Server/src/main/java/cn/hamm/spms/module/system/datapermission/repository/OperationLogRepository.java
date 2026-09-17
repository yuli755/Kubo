package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.mapper.KuboInfraMapper;
import cn.hamm.spms.module.system.datapermission.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>通用操作审计日志仓储</h1>
 *
 * <p>2026-09-18 由 Spring Data JPA 换成 MyBatis-Plus（去 JPA 第一批，写法见
 * {@link DataPermissionDenyLogRepository} 的说明）。）。**语句（含 LIMIT）全部住在 `src/main/resources/mapper/system/datapermission/OperationLogRepository.xml`**（SQL 分层规则：
 * Java 里不写 SQL）—— 这里只留方法声明，列名与排序在 XML 里一眼可读。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface OperationLogRepository extends KuboInfraMapper<OperationLogEntity> {
    /**
     * 最近 200 条操作日志（倒序）
     *
     * @return 日志列表
     */
    List<OperationLogEntity> findTop200ByOrderByIdDesc();

    /**
     * 某资源最近 200 条操作日志（倒序）
     *
     * @param resourceKey 资源标识
     * @return 日志列表
     */
    List<OperationLogEntity> findTop200ByResourceKeyOrderByIdDesc(@Param("resourceKey") String resourceKey);

    /**
     * 删除截止线之前的操作日志（保留期清理）
     *
     * @param cutoffEpochMillis 截止时间（毫秒）
     * @return 删除行数
     */
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
