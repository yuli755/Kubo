package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * <h1>两套 ORM 的行数对账记录</h1>
 *
 * <p>{@link DataPermissionDiffEntity} 回答的是"<em>加条件</em>会不会少行"；本表回答的是
 * "<em>同一棵树交给 MyBatis 去数</em>，数出来的是不是同一批行"。两件事的判据方向不同，所以不共用一张表：
 * 混在一张表里，{@code enforce-readiness} 的正向样本（EXPECTED_FILTER）就会被另一种语义污染 ——
 * "两套 ORM 一致"从来不能证明"这条条件会少行"，反之亦然（§19.36 拍板记在施工图里）。</p>
 *
 * <h2>两个方向不对称，所以两个常量</h2>
 * <p>{@code mapperCount > jpaCount}（{@link #VERDICT_MAPPER_LOOSER}）意味着 Mapper 侧<em>多放行</em>了行：
 * 这是唯一会泄露数据的方向，因此它单独参与放开判据。最常见的成因是"资源登记了某根归属列、实体上没映射" ——
 * SQL 侧没有元模型可问，登记的列全用，于是多出一个 OR 分支，结果集变大。
 * （§19.35 把同一个形状称成"更严"，方向说反了；对账表第一轮就把这件事照出来，纠正见该节末尾。）</p>
 * <p>{@code mapperCount < jpaCount}（{@link #VERDICT_MAPPER_STRICTER}）是反方向：Mapper 少给行，
 * 泄露不了，但有人会看不到本该看到的数据。它不阻塞放开，<em>可必须留痕</em> ——
 * 一个解释不了的差异比十个已知差异危险。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "data_permission_orm_parity_log")
@Description("数据权限 ORM 行数对账")
public class DataPermissionOrmParityEntity extends BaseEntity<DataPermissionOrmParityEntity> {

    /**
     * 两边行数相同：换轨可以放心
     */
    public static final String VERDICT_CONSISTENT = "CONSISTENT";

    /**
     * Mapper 侧少放行：不泄露，但有人少看了数据；不阻塞放开，必须能解释
     */
    public static final String VERDICT_MAPPER_STRICTER = "MAPPER_STRICT";

    /**
     * Mapper 侧多放行：<b>泄露方向</b>，阻塞放开（成因多半是实体少映射了一根归属列）
     */
    public static final String VERDICT_MAPPER_LOOSER = "MAPPER_LOOSE";

    /**
     * 数不出来：渲染失败、片段与参数不吻合、或探针返回了 null
     */
    public static final String VERDICT_ERROR = "ERROR";

    @Description("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Description("数据资源标识")
    @Column(name = "resource_key", nullable = false, length = 128)
    private String resourceKey;

    @Description("动作")
    @Column(name = "action_name", nullable = false, length = 16)
    private String actionName;

    @Description("Mapper 侧数的是哪张物理表（表名解析错了要先在这里看得见）")
    @Column(name = "table_name", length = 128)
    private String tableName;

    @Description("主体用户")
    @Column(name = "user_id")
    private Long userId;

    @Description("主体租户")
    @Column(name = "tenant_id")
    private Long tenantId;

    @Description("JPA 侧加条件后的行数")
    @Column(name = "jpa_count")
    private Long jpaCount;

    @Description("Mapper 侧加同一段片段后的行数")
    @Column(name = "mapper_count")
    private Long mapperCount;

    @Description("结论：CONSISTENT / MAPPER_STRICT / MAPPER_LOOSE / ERROR")
    @Column(name = "verdict", nullable = false, length = 16)
    private String verdict;

    @Description("Mapper 侧片段原文（只到 :dpRule0 这一层，值都是参数）")
    @Column(name = "fragment_sql", length = 512)
    private String fragmentSql;

    @Description("对账耗时（毫秒）")
    @Column(name = "cost_ms")
    private Long costMs;

    @Description("明细（异常原因等）")
    @Column(name = "detail", columnDefinition = "text")
    private String detail;
}
