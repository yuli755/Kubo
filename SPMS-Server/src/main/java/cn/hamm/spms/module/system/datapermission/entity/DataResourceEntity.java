package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>数据权限资源</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "data_resource", uniqueConstraints = @UniqueConstraint(name = "uk_data_resource_key", columnNames = "resource_key"))
@Description("数据权限资源")
public class DataResourceEntity extends BaseEntity<DataResourceEntity> {
    @Description("资源标识")
    @Column(name = "resource_key", nullable = false, length = 128)
    private String resourceKey;

    @Description("数据库表名")
    @Column(name = "table_name", nullable = false, length = 128)
    private String tableName;

    @Description("SQL标准别名")
    @Column(name = "table_alias", length = 64)
    private String tableAlias;

    @Description("租户字段")
    @Column(name = "tenant_column", nullable = false, length = 64)
    private String tenantColumn = "tenant_id";

    @Description("归属字段")
    @Column(name = "owner_columns", length = 512)
    private String ownerColumns;

    @Description("支持动作")
    @Column(name = "supported_actions", nullable = false, length = 256)
    private String supportedActions;

    @Description("资源状态")
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DRAFT";

    @Description("配置版本")
    @Column(name = "version_no", nullable = false)
    private Long versionNo = 1L;
}
