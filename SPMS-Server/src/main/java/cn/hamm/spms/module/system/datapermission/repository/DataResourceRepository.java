package cn.hamm.spms.module.system.datapermission.repository;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import org.springframework.stereotype.Repository;

/**
 * <h1>数据资源仓库</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface DataResourceRepository extends BaseRepository<DataResourceEntity> {
    DataResourceEntity getByResourceKey(String resourceKey);

    /**
     * 按物理表名反查资源
     *
     * <p>存在的理由：HTTP 出口（导出）只有 URL 段可用（{@code /material/export}），
     * 而资源标识是 {@code asset.material} —— 中间这层映射必须由注册表给出，
     * 不能在代码里再造一份"模块名→资源键"的约定（两份真相迟早漂移）。</p>
     */
    DataResourceEntity getByTableName(String tableName);
}
