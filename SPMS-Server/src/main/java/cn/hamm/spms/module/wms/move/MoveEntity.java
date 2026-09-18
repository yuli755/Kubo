package cn.hamm.spms.module.wms.move;

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
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.wms.move.detail.MoveDetailEntity;
import cn.hamm.spms.module.wms.move.enums.MoveStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import static cn.hamm.spms.base.coderule.CodeRuleField.MoveBillCode;

/**
 * <h1>移库单实体</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("move")
@Description("移库单")
public class MoveEntity extends AbstractBaseBillEntity<MoveEntity, MoveDetailEntity> {
    @Description("移库单号")
    @Unique
    @AutoGenerateCode(MoveBillCode)
    @Search
    @Meta
    @Export
    private String billCode;

    @Description("移库状态")
    @Dictionary(value = MoveStatus.class, groups = {WhenAdd.class, WhenUpdate.class})
    @Export(Export.Type.DICTIONARY)
    private Integer status;

    @Description("入库仓库")
    @TableField(exist = false)
    @NotNull(groups = {WhenUpdate.class, WhenAdd.class}, message = "入库仓库")
    private StorageEntity storage;

    /**
     * 外键列 storage_id（MyBatis-Plus 侧的读写走这个标量字段）
     *
     * @apiNote 关系对象标了 {@code @TableField(exist = false)}（MyBatis-Plus 不把对象当列），
     * 这个标量字段才是列：写路径由 {@code RelationMapping#syncForeignKeys} 从关系对象同步过来，
     * 读路径由 {@code RelationHydrator} 按它批量回填关系对象。
     */
    @TableField("storage_id")
    private Long storageId;
}
