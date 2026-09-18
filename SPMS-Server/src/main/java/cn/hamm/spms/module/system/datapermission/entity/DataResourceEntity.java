package cn.hamm.spms.module.system.datapermission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>数据权限资源</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("data_resource")
@Description("数据权限资源")
public class DataResourceEntity extends BaseEntity<DataResourceEntity> {

    @Description("资源标识")
    private String resourceKey;

    @Description("数据库表名")
    private String tableName;

    @Description("SQL标准别名")
    private String tableAlias;

    @Description("租户字段")
    private String tenantColumn = "tenant_id";

    @Description("归属字段")
    private String ownerColumns;

    @Description("支持动作")
    private String supportedActions;

    @Description("条件树可引用的列（CSV；DB 列名或实体属性名均可，两者自动互通）")
    private String filterableColumns;

    @Description("资源状态")
    private String status = "DRAFT";

    @Description("配置版本")
    private Long versionNo = 1L;
}
