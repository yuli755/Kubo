package cn.hamm.spms.module.asset.material;

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
import cn.hamm.spms.module.asset.material.enums.MaterialType;
import cn.hamm.spms.module.asset.material.enums.MaterialUseType;
import cn.hamm.spms.base.coderule.CodeRuleField;
import cn.hamm.spms.framework.curd.audit.OperationLog;
import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import cn.hamm.spms.module.system.unit.UnitEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

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
@Data
@TableName("material")
@Description("物料")
@OperationLog
public class MaterialEntity extends TenantAwareEntity<MaterialEntity> {
    @Description("物料名称")
    @Search
    @Export
    @Unique
    @NotBlank(groups = {WhenAdd.class, WhenUpdate.class}, message = "物料名称不能为空")
    @Meta
    private String name;

    @Description("物料编码")
    @Unique
    @Search
    @AutoGenerateCode(CodeRuleField.MaterialCode)
    @Export
    @Meta
    private String code;

    @Description("规格型号")
    @Search
    @Export
    private String spc;

    @Description("物料类型")
    @Dictionary(value = MaterialType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer materialType;

    @Description("使用方式")
    @Dictionary(value = MaterialUseType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer useType;

    @Description("默认单位")
    @TableField(exist = false)
    @NotNull(groups = {WhenAdd.class, WhenUpdate.class}, message = "默认单位不能为空")
    private UnitEntity unit;

    /**
     * 外键列 unit_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("unit_id")
    private Long unitId;

    @Description("采购标准价")
    @SensitiveField(resourceKey = "asset.material")
    @Export(Export.Type.NUMBER)
    private Double purchasePrice;

    @Description("销售标准价")
    @SensitiveField(resourceKey = "asset.material")
    @Export(Export.Type.NUMBER)
    private Double salePrice;
}
