package cn.hamm.spms.module.channel.customer;

import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Export;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.Phone;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import static cn.hamm.spms.base.coderule.CodeRuleField.CustomerCode;

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
    @Phone(groups = {WhenAdd.class, WhenUpdate.class})
    @Search
    @Export
    private String phone;
}
