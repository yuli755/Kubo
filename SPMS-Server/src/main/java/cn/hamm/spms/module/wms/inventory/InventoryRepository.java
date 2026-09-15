package cn.hamm.spms.module.wms.inventory;

import cn.hamm.spms.base.BaseRepository;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * <h1>数据库连接信息</h1>
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
    InventoryEntity getByMaterialAndStorage(MaterialEntity material, StorageEntity storage);

    /**
     * 查询指定物料在指定生产单元下的库存
     *
     * @param material  物料
     * @param structure 生产单元
     * @return 库存
     */
    InventoryEntity getByMaterialAndStructure(MaterialEntity material, StructureEntity structure);

    /**
     * 原子增加库存数量（阶段 F-3）。
     *
     * <p>算术表达式在数据库内求值，没有"先读后写"窗口——任意交错执行下总量恒等。</p>
     *
     * @param id    库存行 ID
     * @param delta 增量
     * @param now   更新时间戳
     * @return 受影响行数（0 表示行不存在）
     */
    @Modifying
    @Query("update InventoryEntity i set i.quantity = i.quantity + :delta, i.updateTime = :now where i.id = :id")
    int increaseQuantityById(@Param("id") long id, @Param("delta") double delta, @Param("now") long now);

    /**
     * 原子扣减库存数量（阶段 F-3），"扣成负数"时不更新（守卫在 WHERE 中）。
     *
     * @param id    库存行 ID
     * @param minus 扣减量
     * @param now   更新时间戳
     * @return 受影响行数（0 表示行不存在或数量不足）
     */
    @Modifying
    @Query("update InventoryEntity i set i.quantity = i.quantity - :minus, i.updateTime = :now "
            + "where i.id = :id and i.quantity >= :minus")
    int decreaseQuantityById(@Param("id") long id, @Param("minus") double minus, @Param("now") long now);
}
