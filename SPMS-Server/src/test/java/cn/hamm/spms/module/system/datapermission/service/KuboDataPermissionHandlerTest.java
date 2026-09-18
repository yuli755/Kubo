package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.mybatis.KuboDataPermissionHandler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>MyBatis 运行时数据权限处理器契约</h1>
 *
 * <p>不启动 Spring、不连数据库，只验证 statement 绑定、主体策略、受控 SQL 渲染、fail-closed
 * 的边界，以及 SHADOW 档的取证接线（拦截器路径）。真 Mapper/XML 的 H2 回归由
 * {@code *MapperRuntimeInterceptorTest} 覆盖；两次 COUNT 的取数与落库由
 * {@code ShadowDiffServiceCountTest} 覆盖（J4 起没有第二个 ORM 可比）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("KuboDataPermissionHandler · statement 到权限条件")
class KuboDataPermissionHandlerTest {

    @DataResource(key = "asset.material", alias = "material")
    interface DemoMapper extends cn.hamm.spms.base.mapper.KuboBaseMapper<MaterialEntity> {
    }

    /**
     * 泛型参数没被绑定的读侧 Mapper（真实形状：接口<b>忘了</b>写 {@code KuboBaseMapper<MaterialEntity>}）：
     * J4 之后"数哪张表"必须从泛型参数解析出来，解析不到就只能记一条"无证据"，不许按类名猜表名
     */
    @DataResource(key = "asset.material", alias = "material")
    interface UnboundGenericMapper<T> extends cn.hamm.spms.base.mapper.KuboBaseMapper<T> {
    }

    private final DataPermissionAudit audit = mock(DataPermissionAudit.class);
    private final DataPermissionService dataPermissionService = mock(DataPermissionService.class);
    private final DataPermissionProperties properties = new DataPermissionProperties();
    private final ShadowDiffService shadowDiffService = mock(ShadowDiffService.class);
    private final KuboDataPermissionHandler handler;

    KuboDataPermissionHandlerTest() {
        String statementId = DemoMapper.class.getName() + ".page";
        Configuration configuration = new Configuration();
        configuration.addMappedStatement(new MappedStatement.Builder(
                configuration,
                statementId,
                new StaticSqlSource(configuration, SqlFixtures.text("permission-handler/probe.sql")),
                SqlCommandType.SELECT
        ).build());
        DataResourceBindingRegistry bindings = DataResourceBindingRegistry.from(
                configuration, List.of(DemoMapper.class));
        handler = new KuboDataPermissionHandler(
                bindings,
                dataPermissionService,
                properties,
                audit,
                new PermissionRuleTreeBuilder(new PermissionRuleParser()),
                new SqlPermissionRuleRenderer(),
                shadowDiffService);
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    @Test
    @DisplayName("精确绑定的租户策略渲染成带表限定的 AST")
    void rendersBoundTenantPolicy() {
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        PermissionContext context = context(7L, 10L);
        PermissionContextHolder.open(context);
        when(dataPermissionService.requireResource("asset.material")).thenReturn(resource());
        when(dataPermissionService.getPolicies(eq("asset.material"), eq(DataPermissionAction.QUERY), eq(context)))
                .thenReturn(List.of(policy("TENANT")));

        Expression expression = handler.getSqlSegment(table("material"), null,
                DemoMapper.class.getName() + ".page");

        assertTrue(expression.toString().contains("material.tenant_id = 10"), expression.toString());
    }

    @Test
    @DisplayName("statement 未绑定时 enforce 恒假，不能靠 SQL 表名猜资源")
    void unboundStatementDeniesInEnforce() {
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        Expression expression = handler.getSqlSegment(table("material"), null, "unknown.Mapper.page");

        assertEquals("1 = 0", expression.toString());
        verify(audit).deny("unknown.Mapper.page", DataPermissionAudit.REASON_UNREGISTERED,
                "unknown.Mapper.page");
    }

    @Test
    @DisplayName("条件树解析失败时 enforce 恒假")
    void invalidConditionDeniesInEnforce() {
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        PermissionContext context = context(7L, 10L);
        PermissionContextHolder.open(context);
        when(dataPermissionService.requireResource("asset.material")).thenReturn(resource());
        DataPolicyEntity policy = policy("TENANT");
        policy.setConditionJson("{\"sql\":\"1 = 1\"}");
        when(dataPermissionService.getPolicies(eq("asset.material"), eq(DataPermissionAction.QUERY), eq(context)))
                .thenReturn(List.of(policy));

        Expression expression = handler.getSqlSegment(table("material"), null,
                DemoMapper.class.getName() + ".page");

        assertEquals("1 = 0", expression.toString());
        verify(audit).deny(eq("asset.material"), eq(DataPermissionAudit.REASON_PARSE_FAILED), any());
    }

    @Test
    @DisplayName("shadow 只计算并记录，不改写原查询")
    void shadowDoesNotChangeQuery() {
        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        PermissionContext context = context(7L, 10L);
        PermissionContextHolder.open(context);
        when(dataPermissionService.requireResource("asset.material")).thenReturn(resource());
        when(dataPermissionService.getPolicies(eq("asset.material"), eq(DataPermissionAction.QUERY), eq(context)))
                .thenReturn(List.of(policy("TENANT")));

        assertNull(handler.getSqlSegment(table("material"), null, DemoMapper.class.getName() + ".page"));
        verify(audit).shadow(eq("asset.material"), eq("would-filter"), any());

        // J4：mapper 读出口不走 wrapper，SHADOW 档的证据必须由这里交出去（否则 mapper-read 打开时
        // 这些资源一条 EXPECTED_FILTER 样本都不会有）。交出的片段**不带表别名** —— 比对数的是实体
        // 仓储的裸表查询（SELECT COUNT(*) FROM material），带上 material./m. 前缀在那里解析不到表。
        ArgumentCaptor<List<RowPermissionPredicate>> captor = ArgumentCaptor.forClass(List.class);
        verify(shadowDiffService).compare(eq("asset.material"), eq(DataPermissionAction.QUERY),
                eq(MaterialEntity.class), captor.capture(), eq(context));
        assertEquals(1, captor.getValue().size());
        assertEquals("(tenant_id = :dpRule0)", captor.getValue().get(0).sql());
        assertTrue(captor.getValue().get(0).parameters().containsValue(10L));
    }

    @Test
    @DisplayName("泛型参数没绑上实体：不取证，但记一条「没有证据」（不许按类名猜表名）")
    void unboundGenericRecordsNoEntityInsteadOfGuessing() {
        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        PermissionContext context = context(7L, 10L);
        PermissionContextHolder.open(context);
        when(dataPermissionService.requireResource("asset.material")).thenReturn(resource());
        when(dataPermissionService.getPolicies(eq("asset.material"), eq(DataPermissionAction.QUERY), eq(context)))
                .thenReturn(List.of(policy("TENANT")));
        // 另建一个"泛型参数没绑上"的绑定：按类名推表名是被禁止的（§19.34），所以只能记无证据
        Configuration configuration = new Configuration();
        String statementId = UnboundGenericMapper.class.getName() + ".page";
        configuration.addMappedStatement(new MappedStatement.Builder(configuration, statementId,
                new StaticSqlSource(configuration, SqlFixtures.text("permission-handler/probe.sql")),
                SqlCommandType.SELECT).build());
        KuboDataPermissionHandler plain = new KuboDataPermissionHandler(
                DataResourceBindingRegistry.from(configuration, List.of(UnboundGenericMapper.class)),
                dataPermissionService, properties, audit,
                new PermissionRuleTreeBuilder(new PermissionRuleParser()), new SqlPermissionRuleRenderer(),
                shadowDiffService);

        assertNull(plain.getSqlSegment(table("material"), null, statementId));
        verify(audit).shadow(eq("asset.material"), eq("diff:no-entity"), any());
        verify(shadowDiffService, never()).compare(any(), any(), any(), any(), any());
    }

    private static Table table(String name) {
        Table table = new Table();
        table.setName(name);
        return table;
    }

    private static DataResourceEntity resource() {
        return new DataResourceEntity()
                .setResourceKey("asset.material")
                .setStatus("ENABLED")
                .setTableName("material")
                .setTableAlias("material")
                .setTenantColumn("tenant_id")
                .setOwnerColumns("creator_id");
    }

    private static DataPolicyEntity policy(String subjectType) {
        return new DataPolicyEntity()
                .setSubjectType(subjectType)
                .setActionName("QUERY")
                .setConditionJson("{}");
    }

    private static PermissionContext context(Long userId, Long tenantId) {
        return new PermissionContext(userId, tenantId, java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false);
    }
}
