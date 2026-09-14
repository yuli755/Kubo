package cn.hamm.spms.module.wms.inventory;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.Dictionary;
import cn.hamm.airpower.core.annotation.Export;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.wms.inventory.enums.InventoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import static jakarta.persistence.FetchType.EAGER;

/**
 * <h1>库存实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "inventory", uniqueConstraints = {
        // 一行库存 = 一个维度组合。此前库面无任何唯一键，并发「查无则建」会插出
        // 重复行（实测并发入同一新仓两次产生 0~2 行不等），此后出库按单行读，
        // 总量直接少算。storage 与 structure 互斥（NULL 不参与唯一比较），
        // 两个索引各约束一种维度，见 migration/20260916_inventory_dimension_unique.sql。
        @UniqueConstraint(name = "uk_inventory_material_storage", columnNames = {"material_id", "storage_id"}),
        @UniqueConstraint(name = "uk_inventory_material_structure", columnNames = {"material_id", "structure_id"})
})
@Description("库存")
public class InventoryEntity extends TenantAwareEntity<InventoryEntity> {
    @Description("物料信息")
    @ManyToOne(fetch = EAGER)
    private MaterialEntity material;

    @Description("库存数量")
    @Export(Export.Type.NUMBER)
    @Column(columnDefinition = "double(20, 6) UNSIGNED default 0 comment '库存数量'")
    private Double quantity;

    @Description("存储类型")
    @Column(columnDefinition = "int UNSIGNED default 1 comment '存储类型'")
    @Dictionary(value = InventoryType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer type;

    @Description("仓库")
    @ManyToOne(fetch = EAGER)
    private StorageEntity storage;

    @Description("生产单元")
    @ManyToOne(fetch = EAGER)
    private StructureEntity structure;
}
