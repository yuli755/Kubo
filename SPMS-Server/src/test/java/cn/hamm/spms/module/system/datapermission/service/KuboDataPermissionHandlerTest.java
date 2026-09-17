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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>MyBatis 运行时数据权限处理器契约</h1>
 *
 * <p>不启动 Spring、不连数据库，只验证 statement 绑定、主体策略、受控 SQL 渲染和 fail-closed
 * 的边界。真 Mapper/XML 的 H2 行数对账由 {@code MaterialMapperReadParityTest} 覆盖。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("KuboDataPermissionHandler · statement 到权限条件")
class KuboDataPermissionHandlerTest {

    @DataResource(key = "asset.material", alias = "material")
    interface DemoMapper {
    }

    private final DataPermissionAudit audit = mock(DataPermissionAudit.class);
    private final DataPermissionService dataPermissionService = mock(DataPermissionService.class);
    private final DataPermissionProperties properties = new DataPermissionProperties();
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
                new SqlPermissionRuleRenderer());
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
