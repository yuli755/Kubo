package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.FilterIgnore;
import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.module.system.datapermission.annotation.OperationLog;
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
 * <p><b>本实体所有带初始值的字段都标了 {@code @FilterIgnore}，删掉任何一个都会让列表页整页空白。</b>
 * {@code QueryListRequest} 的 filter 就是实体本身，Jackson 反序列化会执行字段初始化器，
 * 于是 {@code status = "DRAFT"} 这类默认值会被 {@code BaseService.buildPredicates}
 * 当成调用方传来的查询条件拼进 WHERE。库里 17 个资源全部 {@code status = "ENABLED"}，
 * 所以 {@code POST /dataResource/getList {"filter":{}}} 恒回 0 行 ——
 * 真机实测「数据资源治理」页因此表头正常、一行数据都没有。</p>
 *
 * <p>同一个陷阱在本模块已经记过两次（{@code DataResourceService#getByResourceKey} 与
 * {@code DataPolicyRepository#existsByResourceIdAndActionNameAndSubjectType} 的注释），
 * 但当时都是<b>绕过</b>（另写一个 repository 方法），没有堵住源头；HTTP 列表路径于是继续中招。
 * 机械守卫见 {@code EntityFilterDefaultTest}。</p>
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
@OperationLog
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
    @FilterIgnore
    private String tenantColumn = "tenant_id";

    @Description("归属字段")
    @Column(name = "owner_columns", length = 512)
    private String ownerColumns;

    @Description("支持动作")
    @Column(name = "supported_actions", nullable = false, length = 256)
    private String supportedActions;

    @Description("条件树可引用的列（CSV；DB 列名或实体属性名均可，两者自动互通）")
    @Column(name = "filterable_columns", length = 512)
    private String filterableColumns;

    @Description("资源状态")
    @Column(name = "status", nullable = false, length = 16)
    @FilterIgnore
    private String status = "DRAFT";

    @Description("配置版本")
    @Column(name = "version_no", nullable = false)
    @FilterIgnore
    private Long versionNo = 1L;
}
