package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.mapper.KuboInfraMapper;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDenyLogEntity;
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
 *   <li>派生查询 {@code findTop50ByResourceKeyOrderByIdDesc}（原来的 {@code Top50}）与
 *       保留期清理 {@code deleteExpiredBefore}（原来是原生 {@code @Query} + {@code @Modifying}）——
 *       **语句一律搬进 {@code src/main/resources/mapper/system/datapermission/DataPermissionDenyLogRepository.xml}**：</li>
 * </ul>
 *
 * <p><b>为什么连 {@code LIMIT 50} 都要进 XML</b>：SQL 分层规则（用户 2026-09-18）是"禁止 {@code .java}
 * 文件编写 SQL，只能使用专属文件编写"，而 {@code last("LIMIT 50")} 与 {@code @Delete("...")} 一样都是
 * 把语句的片段写进 Java —— 门禁 {@code pnpm sql:check} 会直接把它标红。搬进 XML 之后，
 * "这张表最近 50 条怎么取""清理的条件是什么"都变成可 review、可 {@code EXPLAIN} 的文本。</p>
 *
 * <p><b>占位符</b>：JPA 的 {@code :cutoff} → MyBatis 的 {@code #{cutoff}}；参数名用
 * {@code @Param} 显式声明（不依赖 {@code -parameters} 编译参数）。</p>
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
    List<DataPermissionDenyLogEntity> findTop50ByResourceKeyOrderByIdDesc(@Param("resourceKey") String resourceKey);

    /**
     * 最近 50 条拒绝事件（倒序）
     *
     * @return 事件列表
     */
    List<DataPermissionDenyLogEntity> findTop50ByOrderByIdDesc();

    /**
     * 删除截止线之前的拒绝事件（保留期清理）
     *
     * @param cutoffEpochMillis 截止时间（毫秒）
     * @return 删除行数
     */
    int deleteExpiredBefore(@Param("cutoff") long cutoffEpochMillis);
}
