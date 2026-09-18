package cn.hamm.spms.module.channel.saleprice;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Export;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


/**
 * <h1>销售价格实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("sale_price")
@Description("销售价格")
public class SalePriceEntity extends TenantAwareEntity<SalePriceEntity> implements ISalePriceAction {
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

    @Description("客户信息")
    @TableField(exist = false)
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "客户不能为空")
    private CustomerEntity customer;

    /**
     * 外键列 customer_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("customer_id")
    private Long customerId;

    @Description("销售单价")
    @SensitiveField(resourceKey = "channel.salePrice")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "销售单价不能为空")
    @Export(Export.Type.NUMBER)
    private Double price;
}
