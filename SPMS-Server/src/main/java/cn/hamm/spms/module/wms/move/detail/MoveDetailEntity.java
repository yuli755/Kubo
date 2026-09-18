package cn.hamm.spms.module.wms.move.detail;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.base.bill.detail.BaseBillDetailEntity;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


/**
 * <h1>移库明细实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("move_detail")
@Description("移库明细")
public class MoveDetailEntity extends BaseBillDetailEntity<MoveDetailEntity> {
    @Description("库存信息")
    @TableField(exist = false)
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "库存信息")
    private InventoryEntity inventory;

    /**
     * 外键列 inventory_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("inventory_id")
    private Long inventoryId;

    @Description("移动数量")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "移动数量不能为空")
    @Meta
    private Double quantity;

    @Description("已移动数量")
    @Meta
    @ReadOnly
    private Double finishQuantity;
}
