package cn.hamm.spms.module.asset.contract.document;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>合同附件实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("contract_document")
@Description("合同附件")
public class ContractDocumentEntity extends BaseEntity<ContractDocumentEntity> {
    @Description("附件地址")
    @Search
    @NotBlank(groups = {WhenAdd.class, WhenUpdate.class}, message = "附件地址不能为空")
    private String url;

    @Description("名称")
    @Search
    @Meta
    private String name;

    @Description("扩展名")
    @Search
    private String extension;

    @Description("MD5")
    @Search
    private String hashMd5;

    @Description("文件大小")
    private Long size;
}