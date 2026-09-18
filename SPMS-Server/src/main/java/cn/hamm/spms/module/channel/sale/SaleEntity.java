package cn.hamm.spms.module.channel.sale;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.bill.AbstractBaseBillEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.channel.sale.detail.SaleDetailEntity;
import cn.hamm.spms.module.channel.sale.enums.SaleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static cn.hamm.spms.base.coderule.CodeRuleField.SaleBillCode;

/**
 * <h1>采购单实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("sale")
@Description("销售单")
public class SaleEntity extends AbstractBaseBillEntity<SaleEntity, SaleDetailEntity> {
    @Description("销售单号")
    @Unique
    @AutoGenerateCode(SaleBillCode)
    @Search
    @Meta
    private String billCode;

    @Description("销售说明")
    @Length(max = 80, message = "采购事由仅支持输入{min}个{max}个字符")
    @Search
    private String reason;

    @Description("总金额")
    @ReadOnly
    private Double totalPrice;

    @Description("销售状态")
    @Dictionary(value = SaleStatus.class, groups = {WhenAdd.class, WhenUpdate.class})
    private Integer status;

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
}
