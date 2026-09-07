package cn.hamm.spms.module.asset.material.mapper;

import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>读侧 Mapper 第一次真的跑 SQL（H2）</h1>
 *
 * <p>这一格买的不是"权限算得对不对"，是<b>换轨机制成立不成立</b>，三件事：</p>
 * <ol>
 *   <li>打开 {@code kubo.data-permission.mapper-read.enabled} 之后，{@code KuboBaseMapper} 的子接口
 *       真的注册成了 Bean、XML 真的被加载。这两件事任一件没成，启动期都毫无征兆，
 *       第一次调用才炸 {@code Invalid bound statement}；</li>
 *   <li>渲染器产出的片段经 {@code forMyBatis()} 换写后，{@code ${fragment}} 里的
 *       {@code #{...}} <b>真的被参数化</b>。MyBatis 先做 {@code ${}} 文本替换、再解析 {@code #{}}，
 *       推理上成立，但整套 Mapper 侧收口都压在这一条上，所以必须由真 SQL 证明；</li>
 *   <li>同表、同语义条件下，Mapper 数出的行数与 JPA Criteria 数出的相等 —— 这是"两套读语义并存"
 *       的最小证据。它只有在<b>过滤确实生效</b>时才算证据，所以每条相等断言旁边都配一条
 *       "过滤后 &lt; 未过滤"：否则"两边都没过滤"也能把相等跑得很好看。</li>
 * </ol>
 *
 * <p>JPA 侧走属性名 {@code tenantId}、SQL 侧走列名 {@code tenant_id}，这不是疏漏而是两套 ORM 的
 * 固有差一层：换轨时最典型的静默 bug 就是把属性名当列名（表现是"列不存在"）或反过来（表现是
 * 绑到了同名的别的列，直接放宽范围）。所以两边<em>各按各自的约定写</em>，由这条用例保证结果一致。</p>
 *
 * <p>不开事务：种子数据由 JdbcTemplate 直写，JPA 事务不接管那条连接，{@code @Transactional}
 * 回滚不到它。写成"看起来会回滚"的形状，只会让下一个加用例的人留下脏行互相影响 —— 清理因此
 * 放在 {@code @BeforeEach} 里显式 DELETE。表也是这里现建的：{@code schema-test.sql} 只放数据权限
 * 那四张共享表，换轨夹具不该把爆炸半径铺进公共脚本。</p>
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "airpower.redis.enabled=false",
        "airpower.mqtt.enabled=false",
        "kubo.data-permission.mode=off",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("读侧 Mapper：注册、片段参数化、与 JPA 数同一张表")
class MaterialMapperReadParityTest {

    private static final long TENANT_A = 10L;
    private static final long TENANT_B = 20L;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void seed() {
        // unit 表是为 JPA 侧准备的：MaterialEntity 有个 EAGER 的 @ManyToOne unit，
        // COUNT 查询今天不 join，但"少一张表"这种红要留给真出事的那次，不要留给我们自己猜。
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS unit (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS material ("
                + "id BIGINT PRIMARY KEY, tenant_id BIGINT, creator_id BIGINT, unit_id BIGINT)");
        jdbcTemplate.update("DELETE FROM material");
        insert(1L, TENANT_A, 100L);
        insert(2L, TENANT_A, 101L);
        insert(3L, TENANT_B, 102L);
    }

    private void insert(long id, long tenantId, long creatorId) {
        jdbcTemplate.update("INSERT INTO material (id, tenant_id, creator_id) VALUES (?, ?, ?)",
                id, tenantId, creatorId);
    }

    /**
     * JPA 侧的同一件事：数一遍 material，可带条件
     */
    private long countByJpa(Function<Root<MaterialEntity>, Predicate> filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<MaterialEntity> root = query.from(MaterialEntity.class);
        query.select(builder.count(root));
        Predicate predicate = filter == null ? null : filter.apply(root);
        if (predicate != null) {
            query.where(predicate);
        }
        Long count = entityManager.createQuery(query).getSingleResult();
        return count == null ? 0L : count;
    }

    private Long countByMapper(RowPermissionPredicate fragment) {
        return materialMapper.countByPermissionFragment(fragment.myBatisStatementParameters());
    }

    @Test
    @DisplayName("不带片段时两边数到同一张表的同一份行数")
    void unfilteredCountsAgree() {
        Long viaMapper = materialMapper.countByPermissionFragment(
                Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));

        assertNotNull(viaMapper, "XML 语句没被执行：mapper-locations 或 namespace 漂了");
        assertEquals(3L, viaMapper.longValue());
        assertEquals(countByJpa(null), viaMapper.longValue(),
                "Mapper 与 JPA 数到不同行数：它们看的不是同一张表（读侧基类里那条表名警告不是洁癖）");
    }

    @Test
    @DisplayName("租户条件：Mapper 侧与 JPA 侧一致，且过滤后行数确实变少")
    void tenantDimensionAgreesAcrossOrms() {
        Long viaMapper = countByMapper(RowPermissionPredicate.tenant("material", "tenant_id", TENANT_A));
        long viaJpa = countByJpa(root -> entityManager.getCriteriaBuilder()
                .equal(root.get("tenantId"), TENANT_A));

        assertEquals(2L, viaMapper.longValue(), "#{...} 没被参数化，或者片段整个没进 SQL");
        assertEquals(viaJpa, viaMapper.longValue());
        assertTrue(viaMapper < 3L, "过滤后没少行：那上面那句相等是自证空转");
    }

    @Test
    @DisplayName("恒假片段让两边都数到 0（而不是抛错，也不是全放行）")
    void denyFragmentReturnsZero() {
        Long viaMapper = countByMapper(RowPermissionPredicate.deny());
        long viaJpa = countByJpa(root -> entityManager.getCriteriaBuilder().disjunction());

        assertEquals(0L, viaMapper.longValue());
        assertEquals(viaJpa, viaMapper.longValue());
    }

    @Test
    @DisplayName("归属人单值：两边一致，且比全表少")
    void ownerDimensionAgreesAcrossOrms() {
        Long viaMapper = countByMapper(RowPermissionPredicate.owner("material", "creator_id", 101L));
        long viaJpa = countByJpa(root -> entityManager.getCriteriaBuilder()
                .equal(root.get("creatorId"), 101L));

        assertEquals(1L, viaMapper.longValue());
        assertEquals(viaJpa, viaMapper.longValue());
    }

    /**
     * 这里手搓片段是有意的：渲染器把 {@code IN} 展开成 {@code :dpRule0_0}、{@code :dpRule0_1}
     * 两个标量参数（它自己的注释写了理由：{@code IN (:list)} 的展开依赖驱动行为），而本仓的
     * {@code RowPermissionPredicate} 工厂反而是集合值 —— 那种片段会被 {@code forMyBatis()} 拒掉。
     * 所以"逐项展开的 IN 能正常绑定"这件事只能按渲染器的输出形状来验；渲染器形状若变，
     * 红的是它自己的用例（{@code PermissionRuleKernelTest}），不是这里。
     */
    @Test
    @DisplayName("IN 逐项展开的片段：多值维度两边一致")
    void expandedInListBindsItemByItem() {
        RowPermissionPredicate in = new RowPermissionPredicate(
                "(material.creator_id IN (:dpRule0_0, :dpRule0_1))",
                Map.of("dpRule0_0", 100L, "dpRule0_1", 102L));

        Long viaMapper = countByMapper(in);
        long viaJpa = countByJpa(root -> root.get("creatorId").in(100L, 102L));

        assertEquals(2L, viaMapper.longValue(), "多值片段只绑上了第一个参数：#{} 的展开漂了");
        assertEquals(viaJpa, viaMapper.longValue());
    }

    @Test
    @DisplayName("XML 承载片段的键名与常量同源（两处各写一遍就会静默变成「不加条件」）")
    void xmlUsesTheSameFragmentKeyAsTheConstant() throws Exception {
        String xml;
        try (InputStream in = MaterialMapperReadParityTest.class.getResourceAsStream(
                "/mapper/asset/MaterialMapper.xml")) {
            assertNotNull(in, "XML 没进 classpath：src/main/resources 下的路径漂了，CI 会先于我们看不见它");
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(xml.contains("${" + RowPermissionPredicate.FRAGMENT_KEY + "}"),
                "XML 里的片段占位符与 RowPermissionPredicate.FRAGMENT_KEY 不同源");
        assertTrue(xml.contains("FROM material"), "语句没显式写表名：那 MP 的表名解析就成了隐式依赖");
    }
}
