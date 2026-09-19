package cn.hamm.spms.module.wms.output;

import cn.hamm.spms.framework.curd.annotation.Unique;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Dictionary;
import cn.hamm.spms.core.annotation.Export;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.base.bill.AbstractBaseBillEntity;
import cn.hamm.spms.base.coderule.AutoGenerateCode;
import cn.hamm.spms.module.channel.sale.SaleEntity;
import cn.hamm.spms.module.wms.move.MoveEntity;
import cn.hamm.spms.module.wms.output.detail.OutputDetailEntity;
import cn.hamm.spms.module.wms.output.enums.OutputStatus;
import cn.hamm.spms.module.wms.output.enums.OutputType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static cn.hamm.spms.base.coderule.CodeRuleField.OutputBillCode;

/**
 * <h1>领料单实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("output")
@Description("出库单")
public class OutputEntity extends AbstractBaseBillEntity<OutputEntity, OutputDetailEntity> {
    @Description("出库单号")
    @Unique
    @AutoGenerateCode(OutputBillCode)
    @Search
    @Meta
    @Export
    private String billCode;

    @Description("出库状态")
    @Dictionary(value = OutputStatus.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer status;

    @Description("出库类型")
    @Dictionary(value = OutputType.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer type;

    @Description("销售单")
    @TableField(exist = false)
    private SaleEntity sale;

    /**
     * 外键列 sale_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("sale_id")
    private Long saleId;

    @Description("移库单")
    @TableField(exist = false)
    private MoveEntity move;

    /**
     * 外键列 move_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("move_id")
    private Long moveId;
}
