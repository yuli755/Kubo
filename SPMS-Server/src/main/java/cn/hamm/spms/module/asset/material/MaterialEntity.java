package cn.hamm.spms.module.asset.material;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.Dictionary;
import cn.hamm.airpower.core.annotation.Export;
import cn.hamm.airpower.core.annotation.Meta;
import cn.hamm.airpower.curd.annotation.Search;
import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.common.annotation.AutoGenerateCode;
import cn.hamm.spms.module.asset.material.enums.MaterialType;
import cn.hamm.spms.module.asset.material.enums.MaterialUseType;
import cn.hamm.spms.module.system.coderule.enums.CodeRuleField;
import cn.hamm.spms.module.system.datapermission.annotation.OperationLog;
import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import cn.hamm.spms.module.system.unit.UnitEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>物料实体</h1>
 *
 * <p>试点：从 {@code BaseEntity} 换成 {@code TenantAwareEntity}，因此自动获得
 * {@code tenant_id}/{@code creator_id} 两个映射（迁移已加列）与写入期归属填充。
 * 换掉之后 {@code DataPermissionSpecification} 里的 {@code root.get("tenant_id")}
 * 才第一次真的取到路径 —— 之前实体没映射该列，条件生成不出来，
 * 这正是"24 个 Service 接了权限代码却零效果"的机制。
 * 其余 T1/T2 实体按模块同样一行改动接入，不要一次性全换。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "material")
@Description("物料")
@OperationLog
public class MaterialEntity extends TenantAwareEntity<MaterialEntity> {
    @Description("物料名称")
    @Search
    @Export
    @Column(columnDefinition = "varchar(255) default '' comment '物料名称'", unique = true)
    @NotBlank(groups = {WhenAdd.class, WhenUpdate.class}, message = "物料名称不能为空")
    @Meta
    private String name;

    @Description("物料编码")
    @Column(columnDefinition = "varchar(255) default '' comment '物料编码'", unique = true)
    @Search
    @AutoGenerateCode(CodeRuleField.MaterialCode)
    @Export
    @Meta
    private String code;

    @Description("规格型号")
    @Search
    @Export
    @Column(columnDefinition = "varchar(255) default '' comment '规格型号'")
    private String spc;

    @Description("物料类型")
    @Column(columnDefinition = "bigint UNSIGNED default 1 comment '物料类型'")
    @Dictionary(value = MaterialType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer materialType;

    @Description("使用方式")
    @Column(columnDefinition = "bigint UNSIGNED default 1 comment '使用方式'")
    @Dictionary(value = MaterialUseType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer useType;

    @Description("默认单位")
    @ManyToOne
    @NotNull(groups = {WhenAdd.class, WhenUpdate.class}, message = "默认单位不能为空")
    private UnitEntity unit;

    @Description("采购标准价")
    @SensitiveField(resourceKey = "asset.material")
    @Export(Export.Type.NUMBER)
    @Column(columnDefinition = "double(20, 6) UNSIGNED default 0 comment '采购标准价'")
    private Double purchasePrice;

    @Description("销售标准价")
    @SensitiveField(resourceKey = "asset.material")
    @Export(Export.Type.NUMBER)
    @Column(columnDefinition = "double(20, 6) UNSIGNED default 0 comment '销售标准价'")
    private Double salePrice;
}
