package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <h1>ORM 行数对账仓库</h1>
 *
 * <p>与 {@link DataPermissionDiffRepository} 同样的取舍：不继承 {@code BaseRepository} /
 * {@code ICurdRepository}，埋点表多一个 CRUD 端点就多一份越权面。只暴露写入与两个计数。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataPermissionOrmParityRepository extends JpaRepository<DataPermissionOrmParityEntity, Long> {

    /**
     * 某资源最近的对账记录（排障用）
     */
    List<DataPermissionOrmParityEntity> findTop50ByResourceKeyOrderByIdDesc(String resourceKey);

    /**
     * 某资源在某一结论下的条数
     *
     * <p>放开前置检查要的是全量计数（翻最近 50 条会漏，见 {@code DataPermissionDiffRepository} 里
     * 同一条理由），且只依赖 {@code verdict} 一列，不去猜表里还有什么。</p>
     *
     * @param resourceKey 资源标识
     * @param verdict     结论（见 {@code DataPermissionOrmParityEntity.VERDICT_*}）
     * @return 条数
     */
    long countByResourceKeyAndVerdict(String resourceKey, String verdict);
}
