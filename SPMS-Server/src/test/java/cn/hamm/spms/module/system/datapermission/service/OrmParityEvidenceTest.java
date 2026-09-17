package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleOp;
import cn.hamm.spms.module.system.datapermission.rule.SubjectDimensionRules;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity.VERDICT_CONSISTENT;
import static cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity.VERDICT_ERROR;
import static cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity.VERDICT_MAPPER_LOOSER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>第三个 COUNT 真的落进了对账表（H2 + 真 Mapper + 真 JPA）</h1>
 *
 * <p>§19.35 把判据收成一棵树，§19.34 证明了"MyBatis 能把渲染器的片段绑上并数出行"。这一格把两者接起来：
 * 影子比对多跑一次 {@code COUNT}，把结果写进 {@code data_permission_orm_parity_log}，
 * 然后<em>把那张表读回来断言</em> —— 因为这条链的产出物就是证据，指标点和代码评审都替代不了"表里有一行"。</p>
 *
 * <p>同步调 {@link ShadowDiffService#doCompare} 而不是 {@code compare}：后者要先过采样、再进线程池，
 * 测试就会开始赌调度时序（"sleep 100ms 再查表"那种红会随机漂）。生产走异步、测试直接驱动任务体，
 * 是这类"只有真库能验"的用例唯一站得住的形状。</p>
 *
 * <p>四类结论各占一格：一致、更严（可解释但要留痕）、跑失败（也必须留痕）、没探针（<b>不许</b>落一行假的"一致"）。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=off",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("ORM 对账：同一棵树两个方向，行数一致要落表")
class OrmParityEvidenceTest {

    private static final String KEY = "asset.material";
    private static final long TENANT_A = 10L;
    private static final long TENANT_B = 20L;
    private static final long TENANT_C = 30L;

    @Autowired
    private ShadowDiffService shadowDiffService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        // 表结构与 MaterialMapperReadParityTest 一致：material 上多一根实体没有的 owner_id，
        // 那是"两边挑不同列"的探针（这里用它造 MAPPER_STRICT 那条证据）；
        // 对账表由 schema-test.sql 建（生产那侧是迁移脚本），本类不清就会串到下一个用例：
        // H2 的 Spring 上下文在同一次 CI 里是缓存复用的
        SqlFixtures.execute(jdbcTemplate, "orm-parity-evidence/seed.sql");
        insert(1L, TENANT_A, 100L, 999L);
        insert(2L, TENANT_A, 101L, 100L);
        insert(3L, TENANT_B, 102L, 100L);
        insert(4L, TENANT_C, 777L, 777L);
        insert(5L, TENANT_C, 778L, 779L);
    }

    private void insert(long id, long tenantId, long creatorId, long ownerId) {
        jdbcTemplate.update(SqlFixtures.text("orm-parity-evidence/insert_material.sql"),
                id, tenantId, creatorId, ownerId);
    }

    private static PermissionContext context(Long userId, Long tenantId) {
        return new PermissionContext(userId, tenantId, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    private static DataResourceEntity resource(String ownerColumns) {
        DataResourceEntity resource = new DataResourceEntity();
        resource.setResourceKey(KEY);
        resource.setTableName("material");
        resource.setTenantColumn("tenant_id");
        resource.setOwnerColumns(ownerColumns);
        return resource;
    }

    private static DataPolicyEntity policy(String subjectType, Long subjectId) {
        DataPolicyEntity policy = new DataPolicyEntity();
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId);
        return policy;
    }

    /**
     * JPA 侧的"enforce 会注入的条件"：与生产同一个编译器、同一批树，不另拼一遍
     */
    private static Specification<MaterialEntity> guarded(List<PermissionRule> rules, PermissionContext context) {
        JpaPermissionRuleCompiler local = new JpaPermissionRuleCompiler();
        return (root, query, builder) -> {
            List<Predicate> parts = new ArrayList<>();
            for (PermissionRule rule : rules) {
                Predicate predicate = local.compile(rule, root, builder, new PermissionRuleContext(context));
                if (predicate != null) {
                    parts.add(predicate);
                }
            }
            return parts.isEmpty() ? builder.conjunction() : builder.and(parts.toArray(new Predicate[0]));
        };
    }

    private Map<String, Object> parityRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                SqlFixtures.text("orm-parity-evidence/parity_rows.sql"));
        assertEquals(1, rows.size(), "对账证据应当恰好一条；" + rows);
        // 别名一律带双引号的理由写在 parity_rows.sql 里（H2 会把裸标签大写化）
        return rows.get(0);
    }

    private long countRows() {
        Long count = jdbcTemplate.queryForObject(
                SqlFixtures.text("orm-parity-evidence/count_parity_rows.sql"), Long.class);
        return count == null ? 0L : count;
    }

    @Test
    @DisplayName("租户档：两边都数到 2 行 ⇒ 落一条 CONSISTENT")
    void consistentEvidenceLandsInTheTable() {
        PermissionContext context = context(1L, TENANT_A);
        PermissionRule rule = SubjectDimensionRules.forPolicy(resource(null), policy("TENANT", null), context).rule();
        List<PermissionRule> rules = List.of(rule);

        shadowDiffService.doCompare(KEY, DataPermissionAction.QUERY, MaterialEntity.class, guarded(rules, context),
                1L, TENANT_A, new ShadowDiffService.OrmParityProbe("material", rules, context));

        Map<String, Object> row = parityRow();
        assertEquals(VERDICT_CONSISTENT, row.get("verdict"));
        assertEquals(2L, ((Number) row.get("jpaCount")).longValue());
        assertEquals(2L, ((Number) row.get("mapperCount")).longValue());
        // 表名是登记的那一个，不是从实体名推的（§19.34 的坑在这里看得见）
        assertEquals("material", row.get("tableName"));
        assertNotNull(row.get("fragmentSql"), "没留片段原文，事后就没法判断两边各用了哪些列");
        assertTrue(String.valueOf(row.get("fragmentSql")).contains("tenant_id"), String.valueOf(row.get("fragmentSql")));
    }

    @Test
    @DisplayName("实体少映射一列 ⇒ 落 MAPPER_LOOSE：换轨前必须解释掉的那一条")
    void divergenceIsRecordedNotSilenced() {
        PermissionContext context = context(100L, TENANT_C);
        // 登记 creator_id,owner_id：owner_id 在 material 上有列、实体上没有属性
        // ⇒ JPA 只绑得上 creator_id（1 行），SQL 侧按登记全取并 OR（creator_id=100 OR owner_id=100 ⇒ 3 行）
        List<PermissionRule> rules = List.of(SubjectDimensionRules
                .forPolicy(resource("creator_id,owner_id"), policy("USER", null), context).rule());

        shadowDiffService.doCompare(KEY, DataPermissionAction.QUERY, MaterialEntity.class, guarded(rules, context),
                100L, TENANT_C, new ShadowDiffService.OrmParityProbe("material", rules, context));

        Map<String, Object> row = parityRow();
        assertEquals(1L, ((Number) row.get("jpaCount")).longValue());
        assertEquals(3L, ((Number) row.get("mapperCount")).longValue());
        // 方向：Mapper 侧多放行 2 行。§19.35 把这个形状叫「SQL 侧更严」，写反了 ——
        // 多认一列是多加一个 OR 分支，只会让结果集变大。对账表第一次跑就把它归成 LOOSE，
        // 而 LOOSE 是阻塞放开的那一档：要么把 owner_id 补进实体映射，要么正面接受可见范围变宽。
        assertEquals(VERDICT_MAPPER_LOOSER, row.get("verdict"),
                "LOOSE 不是噪声，它是唯一朝「泄露」方向偏的一档");
        assertTrue(String.valueOf(row.get("fragmentSql")).contains("owner_id"),
                "片段没带 owner_id，说明 SQL 侧也悄悄跳过了那一列 —— 那这条证据没在比它声称要比的东西");
    }

    @Test
    @DisplayName("SQL 侧渲染拒绝的形状 ⇒ 落一条 ERROR（跑不动也要有痕迹）")
    void renderFailureBecomesAnErrorRow() {
        PermissionContext context = context(1L, TENANT_A);
        // 候选列写成关联路径：§19.35 里 SQL 侧明确抛（不猜列名），这一格让它变成一行可查的证据
        PermissionRule rule = new PermissionRule.Dimension(PermissionRuleOp.EQ, List.of("storage.id"), 7L, false);

        shadowDiffService.doCompare(KEY, DataPermissionAction.QUERY, MaterialEntity.class, guarded(List.of(rule), context),
                1L, TENANT_A, new ShadowDiffService.OrmParityProbe("material", List.of(rule), context));

        Map<String, Object> row = parityRow();
        assertEquals(VERDICT_ERROR, row.get("verdict"));
        assertNotNull(row.get("detail"), "异常没进 detail，事后就只剩一个 ERROR 字符串");
        // 只断言「没数出来」这件事：jpaCount 在这里取决于 JPA 侧对该候选是绑不上（无条件、5 行）
        // 还是抛（比对失败、null），两种都算这条证据成立；把数字写死就成了下次重构的枷锁
        assertNull(row.get("mapperCount"), "没数出来就写 null，不许写 0（0 会被读成「Mapper 侧认为该看 0 行」）");
    }

    @Test
    @DisplayName("真实业务筛选先缩小基线，再与 Mapper 权限 COUNT 对账")
    void filteredEvidenceUsesTheSameBusinessSet() {
        jdbcTemplate.update(SqlFixtures.text("orm-parity-evidence/set_material_names.sql"));
        PermissionContext context = context(1L, TENANT_A);
        PermissionRule rule = SubjectDimensionRules.forPolicy(resource(null), policy("TENANT", null), context).rule();
        List<PermissionRule> rules = List.of(rule);
        Specification<MaterialEntity> business = (root, query, builder) -> builder.equal(root.get("name"), "A");

        shadowDiffService.doCompare(KEY, DataPermissionAction.QUERY, MaterialEntity.class, business,
                guarded(rules, context), 1L, TENANT_A,
                new ShadowDiffService.OrmParityProbe("material", rules, context,
                        Map.of("filter", new MaterialEntity().setName("A"))));

        Map<String, Object> row = parityRow();
        assertEquals(VERDICT_CONSISTENT, row.get("verdict"));
        assertEquals(1L, ((Number) row.get("jpaCount")).longValue());
        assertEquals(1L, ((Number) row.get("mapperCount")).longValue());
    }

    @Test
    @DisplayName("没登记探针的资源：不落任何一行（缺证据不等于一致）")
    void unregisteredResourceWritesNothing() {
        PermissionContext context = context(1L, TENANT_A);
        List<PermissionRule> rules = List.of(SubjectDimensionRules
                .forPolicy(resource(null), policy("TENANT", null), context).rule());

        shadowDiffService.doCompare("demo.unregistered", DataPermissionAction.QUERY, MaterialEntity.class,
                guarded(rules, context), 1L, TENANT_A,
                new ShadowDiffService.OrmParityProbe("demo_unregistered", rules, context));

        assertEquals(0L, countRows(),
                "探针没登记的资源必须什么都不写：写一条 CONSISTENT 就是把「没比过」伪装成「比过且一致」");
    }

}
