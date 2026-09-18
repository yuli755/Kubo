package cn.hamm.spms.module.channel.customer;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Export;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.Phone;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static cn.hamm.spms.base.coderule.CodeRuleField.CustomerCode;

/**
 * <h1>供应商实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("customer")
@Description("客户")
public class CustomerEntity extends TenantAwareEntity<CustomerEntity> {
    @Description("客户名称")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "客户名称不能为空")
    @Search
    @Meta
    @Export
    private String name;

    @Description("客户编码")
    @Unique
    @AutoGenerateCode(CustomerCode)
    @Search
    @Meta
    @Export
    private String code;

    @Description("联系电话")
    @Phone(groups = {WhenAdd.class, WhenUpdate.class})
    @Search
    @Export
    private String phone;
}
