package cn.hamm.spms.module.channel.purchase;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.bill.AbstractBaseBillEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailEntity;
import cn.hamm.spms.module.channel.purchase.enums.PurchaseStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static cn.hamm.spms.base.coderule.CodeRuleField.PurchaseBillCode;

/**
 * <h1>采购单实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("purchase")
@Description("采购单")
public class PurchaseEntity extends AbstractBaseBillEntity<PurchaseEntity, PurchaseDetailEntity> {
    @Description("采购单号")
    @Unique
    @AutoGenerateCode(PurchaseBillCode)
    @Search
    @Meta
    private String billCode;

    @Description("采购事由")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "采购事由")
    @Length(max = 80, message = "采购事由最多允许输入{max}个字符")
    @Search
    private String reason;

    @Description("总金额")
    @ReadOnly
    private Double totalPrice;

    @Description("实际金额")
    @ReadOnly
    private Double totalRealPrice;

    @Description("采购状态")
    @Dictionary(value = PurchaseStatus.class, groups = {WhenAdd.class, WhenUpdate.class})
    private Integer status;
}
