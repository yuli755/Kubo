package cn.hamm.spms.module.asset.contract;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Export;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import cn.hamm.spms.module.asset.contract.document.ContractDocumentEntity;
import cn.hamm.spms.module.asset.contract.enums.ContractStatus;
import cn.hamm.spms.module.asset.contract.enums.ContractType;
import cn.hamm.spms.module.asset.contract.participant.ParticipantEntity;
import cn.hamm.spms.base.coderule.CodeRuleField;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 * <h1>合同实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("contract")
@Description("合同")
public class ContractEntity extends TenantAwareEntity<ContractEntity> {
    @Description("合同编码")
    @Search
    @Unique
    @AutoGenerateCode(CodeRuleField.ContractCode)
    @Meta
    @Export
    private String code;

    @Description("合同名称")
    @Search
    @NotBlank(groups = {WhenAdd.class, WhenUpdate.class}, message = "合同名称不能为空")
    @Meta
    @Export
    private String name;

    @Description("合同内容")
    @Search
    private String content;

    @Description("开始时间")
    private Long startTime;

    @Description("结束时间")
    private Long endTime;

    @Description("合同类型")
    @Dictionary(value = ContractType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer type;

    @Description("合同状态")
    @Dictionary(value = ContractStatus.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer status;

    @Description("附件列表")
    @TableField(exist = false)
    private Set<ContractDocumentEntity> documentList;

    @Description("参与方列表")
    @TableField(exist = false)
    private Set<ParticipantEntity> participantList;
}
