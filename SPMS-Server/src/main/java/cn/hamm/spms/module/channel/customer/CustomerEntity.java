package cn.hamm.spms.module.channel.customer;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Export;
import cn.hamm.spms.framework.web.annotation.Meta;
import cn.hamm.spms.framework.web.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.common.annotation.AutoGenerateCode;
import cn.hamm.spms.framework.util.PhoneRules;
import cn.hamm.spms.module.system.datapermission.annotation.OperationLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import static cn.hamm.spms.module.system.coderule.enums.CodeRuleField.CustomerCode;

/**
 * <h1>供应商实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "customer")
@Description("客户")
@OperationLog
public class CustomerEntity extends TenantAwareEntity<CustomerEntity> {
    @Description("客户名称")
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "客户名称不能为空")
    @Column(columnDefinition = "varchar(255) default '' comment '客户名称'")
    @Search
    @Meta
    @Export
    private String name;

    @Description("客户编码")
    @Column(columnDefinition = "varchar(255) default '' comment '客户编码'", unique = true)
    @AutoGenerateCode(CustomerCode)
    @Search
    @Meta
    @Export
    private String code;

    @Description("联系电话")
    @Column(columnDefinition = "varchar(255) default '' comment '联系电话'")
    @Pattern(regexp = PhoneRules.OPTIONAL_PHONE, message = PhoneRules.INVALID_PHONE_MESSAGE,
            groups = {WhenAdd.class, WhenUpdate.class})
    @Search
    @Export
    private String phone;
}
