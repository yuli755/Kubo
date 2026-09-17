package cn.hamm.spms.module.asset.material.mapper;

import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import cn.hamm.spms.module.system.datapermission.rule.SubjectDimensionRules;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>一棵树、两个渲染方向、同一张真表（H2）</h1>
 *
 * <p>§19.34 验的是"机制"（Mapper 能不能注册、{@code ${}} 里的 {@code #{}} 能不能参数化）；
 * 这一格验的是"合一之后两边没有分家"：条件只由 {@link SubjectDimensionRules} 生成一次，
 * JPA 侧交给 {@link JpaPermissionRuleCompiler}、Mapper 侧交给 {@link SqlPermissionRuleRenderer}
 * 再经 {@link RowPermissionPredicate#forMyBatis()} 换写，然后在<b>同一份数据</b>上各数一次行数。</p>
 *
 * <p>为什么要在真库（哪怕是 H2）上比，而不比字符串：字符串相等只说明两个渲染器写了同样的模板，
 * 不说明<b>它们各自绑得上各自能绑的列</b>。而"绑不上"恰好是这套判据唯一会静默失效的地方 ——
 * 合一之前它长了两份，两份各自静默。</p>
 *
 * <h2>两边不一致是被允许、并且要写下来的</h2>
 * <p>JPA 侧按实体元模型试绑，绑不上的候选列会跳过；SQL 侧没有元模型可问，只能按登记的列全取再 OR。
 * 于是"数据库有这一列、实体没映射"时 <b>SQL 侧多一个 OR 分支，结果集更大 —— 也就是更松</b>。
 * 这个方向不能靠"多认的那列本来就是他的数据"放过：多放行就是泄露面变大，所以 §19.36 把它做成对账表里的
 * {@code MAPPER_LOOSE}，并且是放开前置检查里唯一阻塞的那一档（本类断言数与方向，落库那条在
 * {@code OrmParityEvidenceTest}）。反过来，若哪天有人为了让两边数字相等而给 SQL 侧补一次实体元模型查询
 * （绑不上就跳过），那是把"更松"改成"少放行"：用户会开始看不见本该看见的行，而这个用例就是
 * 他必须正面回答的问题。</p>
 *
 * <p>不开事务：种子数据由 JdbcTemplate 直写，JPA 事务不接管那条连接，{@code @Transactional}
 * 回滚不到它 —— 写成"看起来会回滚"只会让下一个加用例的人留下脏行互相影响。表也是这里现建的
 * （{@code schema-test.sql} 只放数据权限那四张共享表）。</p>
 *
 * <h2>本类第一次跑红留下的两条</h2>
 * <p>一是"换写只能调一次"这个约定没被守住：{@code countByMapper} 先 {@code forMyBatis()} 再取
 * {@code myBatisStatementParameters()}（后者内部还要换写一次），于是抛
 * "parameter map carries keys the fragment never references"。把测试改成"只调一次"只是把约定藏起来，
 * 下一个调用点照样会忘 —— 所以现在 {@code forMyBatis()} 对已换写的片段是幂等的（真没被引用的键仍然红）。</p>
 * <p>二是"取不到值"那一档从上下文不可达（见下面那条注释）：它是渲染器的兜底，不是线上路径；
 * 本类不为不可达的形状造用例，但也不许它顺手写成放行。</p>
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=off",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("读侧合一：同一棵树在 JPA 与 Mapper 两边数出同一份行")
class MaterialMapperReadParityTest {

    private static final long TENANT_A = 10L;
    private static final long TENANT_B = 20L;
    private static final long TENANT_C = 30L;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private final SqlPermissionRuleRenderer renderer = new SqlPermissionRuleRenderer();
    private final JpaPermissionRuleCompiler compiler = new JpaPermissionRuleCompiler();

    @BeforeEach
    void seed() {
        SqlFixtures.execute(jdbcTemplate, "material-read-parity/seed.sql");
        insert(1L, TENANT_A, 100L, 999L);
        insert(2L, TENANT_A, 101L, 100L);
        insert(3L, TENANT_B, 102L, 100L);
        insert(4L, TENANT_C, 777L, 777L);
        insert(5L, TENANT_C, 778L, 779L);
    }

    private void insert(long id, long tenantId, long creatorId, long ownerId) {
        jdbcTemplate.update(SqlFixtures.text("material-read-parity/insert_material.sql"),
                id, tenantId, creatorId, ownerId);
    }

    private DataResourceEntity resource(String ownerColumns) {
        DataResourceEntity resource = new DataResourceEntity();
        resource.setResourceKey("asset.material");
        resource.setTableName("material");
        resource.setTenantColumn("tenant_id");
        resource.setOwnerColumns(ownerColumns);
        return resource;
    }

    private DataPolicyEntity policy(String subjectType, Long subjectId) {
        DataPolicyEntity policy = new DataPolicyEntity();
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId);
        return policy;
    }

    private PermissionContext context(Long userId, Long tenantId) {
        return new PermissionContext(userId, tenantId, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    /**
     * 判据只生成一次
     */
    private PermissionRule tree(DataResourceEntity resource, DataPolicyEntity policy, PermissionContext context) {
        SubjectDimensionRules.SubjectRule subject = SubjectDimensionRules.forPolicy(resource, policy, context);
        assertTrue(subject.rule().contributes(), "这一维根本没生成条件，比对无意义");
        return subject.rule();
    }

    private long countByJpa(PermissionRule rule, PermissionContext context) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<MaterialEntity> root = query.from(MaterialEntity.class);
        query.select(builder.count(root));
        Predicate predicate = compiler.compile(rule, root, builder, new PermissionRuleContext(context));
        if (predicate != null) {
            query.where(predicate);
        }
        Long count = entityManager.createQuery(query).getSingleResult();
        return count == null ? 0L : count;
    }

    private long countByMapper(PermissionRule rule, PermissionContext context) {
        RowPermissionPredicate rendered = renderer.render(rule, "material", new PermissionRuleContext(context));
        assertNotNull(rendered, "渲染器判定这一维不贡献条件：那这一格比的是什么？");
        Long count = materialMapper.countByPermissionFragment(rendered.forMyBatis().myBatisStatementParameters());
        assertNotNull(count, "XML 语句没被执行：mapper-locations 或 namespace 漂了");
        return count;
    }

    @Test
    @DisplayName("不加条件时两边数到同一张表的同一份行数")
    void bothPathsReadTheSameTable() {
        Long viaMapper = materialMapper.countByPermissionFragment(
                Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));

        assertEquals(5L, viaMapper.longValue());
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<MaterialEntity> root = query.from(MaterialEntity.class);
        query.select(builder.count(root));
        assertEquals(5L, entityManager.createQuery(query).getSingleResult().longValue(),
                "Mapper 与 JPA 数到不同的行数：它们看的不是同一张表（读侧基类里那条表名警告不是洁癖）");
    }

    @Test
    @DisplayName("租户档：同一棵树两边行数一致，且确实少了行")
    void tenantDimensionParity() {
        PermissionContext context = context(1L, TENANT_A);
        PermissionRule rule = tree(resource(null), policy("TENANT", null), context);

        long viaMapper = countByMapper(rule, context);
        long viaJpa = countByJpa(rule, context);

        assertEquals(2L, viaMapper, "#{...} 没被参数化，或者片段整个没进 SQL");
        assertEquals(viaJpa, viaMapper);
        assertTrue(viaMapper < 5L, "过滤后没少行：那句相等就是自证空转");
    }

    // 这里原本有一条"租户上下文缺失 ⇒ 两边都给空结果"的用例，CI 把它挡在门外：PermissionContext 的
    // 紧凑构造器要求 userId / tenantId 非空，所以"这一维取不到值"从上下文入口根本不可达。
    // 兜底语义仍然要测、要存在（片段逃进 SQL 之前必须恒假），但只能在节点层面测，
    // 见 SubjectDimensionUnificationTest#nullValueDenies —— 记在这儿是为了别让下一次又把它写成线上路径。

    @Test
    @DisplayName("负责人档：实体没映射的列只有 SQL 侧在用 ⇒ SQL 多放行（更松），不是更严")
    void unmappedOwnerColumnMakesMapperSideLooser() {
        // tenantId 必须给值（上下文不允许 null）；本用例只挂 USER 一维，租户不参与比较，取哪个租户都一样
        PermissionContext context = context(100L, TENANT_C);
        // 登记 creator_id,owner_id 两列：owner_id 在 material 上有列，但实体没有 ownerId 属性
        PermissionRule rule = tree(resource("creator_id,owner_id"), policy("USER", null), context);

        long viaJpa = countByJpa(rule, context);
        long viaMapper = countByMapper(rule, context);

        RowPermissionPredicate fragment = renderer.render(rule, "material", new PermissionRuleContext(context));
        assertTrue(fragment.sql().contains("owner_id"), "SQL 侧把绑不上的候选悄悄丢了：那它就成了「少一个条件」的那一方");
        // JPA：只有 creator_id=100 ⇒ 1 行；SQL：creator_id=100 OR owner_id=100 ⇒ 3 行
        assertEquals(1L, viaJpa);
        assertTrue(viaMapper > viaJpa,
                "SQL 侧多认 owner_id 这一列 ⇒ 命中更多行。方向必须是 Mapper 更多（更松）：\n"
                        + "对账表会把它记成 MAPPER_LOOSE 并阻塞放开");
    }

    @Test
    @DisplayName("恒假片段让两边都数到 0（而不是抛错，也不是全放行）")
    void denyFragmentReturnsZero() {
        Long viaMapper = materialMapper.countByPermissionFragment(
                RowPermissionPredicate.deny().myBatisStatementParameters());

        assertEquals(0L, viaMapper.longValue());
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
