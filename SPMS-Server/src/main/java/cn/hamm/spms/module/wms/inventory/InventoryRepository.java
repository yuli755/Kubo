package cn.hamm.spms.module.wms.inventory;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>库存仓储（CRUD 部分）</h1>
 *
 * <p>两条原子增减语句（{@code increaseQuantityById} / {@code decreaseQuantityById}）已于 2026-09-18
 * 按 SQL 分层规则搬进 {@code mapper/wms/InventoryWriteMapper.xml}：它们是写侧语句，必须无条件注册
 * （不能跟着读侧灰度开关消失），所以住在 {@link cn.hamm.spms.module.wms.inventory.mapper.InventoryWriteMapper}。
 * 本接口剩下的都是 CRUD / 派生查询，等 CRUD 引擎换轨那一批整体切到 MyBatis-Plus。</p>
 *
 * @author Hamm.cn
 */
@Repository
public interface InventoryRepository extends BaseRepository<InventoryEntity> {
    /**
     * 查询指定物料在指定仓库的库存
     *
     * @param material 物料
     * @param storage  仓库
     * @return 库存
     */
    default InventoryEntity getByMaterialAndStorage(MaterialEntity material, StorageEntity storage) {
        return selectOne(new QueryWrapper<InventoryEntity>()
                .eq("material_id", material.getId())
                .eq("storage_id", storage.getId()));
    }

    /**
     * 查询指定物料在指定生产单元下的库存
     *
     * @param material  物料
     * @param structure 生产单元
     * @return 库存
     */
    default InventoryEntity getByMaterialAndStructure(MaterialEntity material, StructureEntity structure) {
        return selectOne(new QueryWrapper<InventoryEntity>()
                .eq("material_id", material.getId())
                .eq("structure_id", structure.getId()));
    }

