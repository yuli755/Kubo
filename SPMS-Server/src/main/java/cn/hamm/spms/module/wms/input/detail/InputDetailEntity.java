package cn.hamm.spms.module.wms.input.detail;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.framework.curd.annotation.InputOnly;
import cn.hamm.spms.base.bill.detail.BaseBillDetailEntity;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


/**
 * <h1>入库明细实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("input_detail")
@Description("入库明细")
public class InputDetailEntity extends BaseBillDetailEntity<InputDetailEntity> {
    @Description("物料信息")
    @TableField(exist = false)
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "物料不能为空")
    private MaterialEntity material;

    /**
     * 外键列 material_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("material_id")
    private Long materialId;

    @Description("入库数量")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "入库数量不能为空")
    @Meta
    private Double quantity;

    @Description("已入库数量")
    @Meta
    @ReadOnly
    private Double finishQuantity;

    /**
     * 入库目标仓库（只作入参）
     *
     * @apiNote 明细表没有仓库列：这个仓库是"这次入库进哪个库"的请求参数，
     * 服务端拿它去找/建库存行（{@code InputService#afterDetailFinishAdded}），值落在 inventory 上。
     * 所以它标 {@link InputOnly} —— 不落库、不参与外键同步、也回填不了（没有列可读）。
     */
    @Description("仓库")
    @TableField(exist = false)
    @InputOnly
    private StorageEntity storage;
}
