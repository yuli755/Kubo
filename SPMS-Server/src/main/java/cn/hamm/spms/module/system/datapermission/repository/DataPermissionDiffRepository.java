package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h1>影子比对差异仓库</h1>
 *
 * <p>刻意不继承 {@code BaseRepository}（那套 CRUD 会连带出 list/page/save/export 端点，
 * 埋点表多一个端点就多一份越权面），也不继承 AirPower 的 {@code ICurdRepository}
 * （它假设实体参与通用 CRUD 契约）。用裸 {@code JpaRepository} 只暴露写入与按资源查询。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataPermissionDiffRepository extends JpaRepository<DataPermissionDiffEntity, Long> {

    /**
     * 某资源最近的差异（看板/排障用）
     */
    List<DataPermissionDiffEntity> findTop50ByResourceKeyOrderByIdDesc(String resourceKey);

    /**
     * 某资源在某一判定下的条数
     *
     * <p>为「逐资源放开」的前置检查准备：判断"影子比对是否已经跑过、有没有异常"要的是
     * <b>全量计数</b>，翻最近 50 条会漏（差异很多时前 50 条可能全是正常的，而差异很少时
     * 50 条又足够把样本之外的异常藏起来）。用 {@code countBy} 也让判据只依赖 verdict 一列，
     * 不去猜表里还有什么。</p>
     *
     * @param resourceKey 资源标识
     * @param verdict     判定（见 {@code DataPermissionDiffEntity.VERDICT_*}）
     * @return 条数
     */
    long countByResourceKeyAndVerdict(String resourceKey, String verdict);

    /**
     * 删除指定时间点之前的差异证据。
     *
     * <p>使用原生 SQL 是刻意的：{@code create_time} 来自 AirPower 的基类，应用 Java 属性名不是本模块
     * 的契约；按物理列写可以让迁移脚本、归档任务与实体映射认同一件事。调用方只传截止毫秒数，
     * 不接受资源键或排序片段。</p>
     *
     * @param cutoffEpochMillis 截止时间（该时刻之前的记录可删除）
     * @return 删除行数
     */
    @Modifying
    @Transactional
    @Query(value = "delete from data_permission_diff_log where create_time < :cutoff", nativeQuery = true)
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
