package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
