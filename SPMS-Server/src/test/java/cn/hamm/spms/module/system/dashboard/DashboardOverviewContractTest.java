package cn.hamm.spms.module.system.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>业务概况聚合契约自检（P2 · 数据权限感知看板）</h1>
 *
 * <p>钉住两点：① 聚合返回 14 个资源、资源键与 {@code @DataResource(key)} 同域且顺序稳定；
 * ② 计数确实流经各资源的 {@code getMapperCount}（不是硬编码 0 —— 给 {@code purchase} 造 3 行，
 * 断言对应项 count==3，其余空表为 0）。</p>
 *
 * <p>不覆盖「权限过滤是否生效」：那是十四个资源运行时测试逐资源钉过的（{@code countByPermission}
 * 在 enforce + 租户上下文下返回过滤后行数）。本类只验证<b>聚合这一面</b>的接线，因此档位钉在
 * {@code mode=off}（无权限条件注入、无 shadow 提交副作用），只要求 14 张表存在。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "airpower.redis.enabled=false",
        "airpower.mqtt.enabled=false",
        // 独立的 H2 库：本类只建最小 business 表（SELECT COUNT(1) 不需要实体全列），
        // 若与十四个运行时测试共享 testdb，最小表会顶掉完整 schema 导致对方 INSERT 语法失败。
        "spring.datasource.url=jdbc:h2:mem:dashboarddb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=RUNSCRIPT FROM 'classpath:schema-test.sql'",
        "kubo.data-permission.mode=off",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("业务概况聚合契约自检")
class DashboardOverviewContractTest {

    /** 物理表名（与各 Mapper XML 的 {@code FROM} 一致），顺序与展示顺序无关 */
    private static final List<String> TABLE_NAMES = List.of(
            "purchase", "sale", "input", "output", "inventory", "move",
            "orders", "plan", "picking", "customer", "supplier",
            "purchase_price", "sale_price", "material"
    );

    /** 期望的资源键（与展示顺序一致，DOT 记号，与后端 {@code @DataResource(key)} 同域） */
    private static final List<String> EXPECTED_KEYS = List.of(
            "channel.purchase", "channel.sale", "wms.input", "wms.output", "wms.inventory", "wms.move",
            "mes.order", "mes.plan", "mes.picking", "channel.customer", "channel.supplier",
            "channel.purchasePrice", "channel.salePrice", "asset.material"
    );

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("overview 返回 14 个资源，键与标签契约稳定")
    void overviewReturnsContractualKeysAndLabels() {
        seedTables();

        List<DashboardOverviewItem> items = dashboardService.overview();

        assertEquals(EXPECTED_KEYS.size(), items.size(), "业务概况应覆盖全部 14 个资源");
        for (int i = 0; i < items.size(); i++) {
            assertEquals(EXPECTED_KEYS.get(i), items.get(i).getResourceKey(),
                    "第 " + i + " 个资源键与契约不符");
        }
        for (DashboardOverviewItem item : items) {
            assertFalse(item.getLabel() == null || item.getLabel().isBlank(),
                    item.getResourceKey() + " 缺少人看名称");
            assertTrue(item.getCount() >= 0, item.getResourceKey() + " 计数不能为负");
        }
    }

    @Test
    @DisplayName("计数流经各资源 getMapperCount，非硬编码")
    void countFlowsThroughMapperReadExit() {
        seedTables();
        // 给 purchase 造 3 行：聚合里 channel.purchase 应为 3，其余空表为 0。
        jdbcTemplate.update("INSERT INTO purchase (id) VALUES (1), (2), (3)");

        Map<String, Long> counts = dashboardService.overview().stream()
                .collect(java.util.stream.Collectors.toMap(
                        DashboardOverviewItem::getResourceKey,
                        DashboardOverviewItem::getCount));

        assertEquals(3L, counts.get("channel.purchase"), "purchase 计数应来自数据库实际行数");
        assertEquals(0L, counts.get("channel.sale"), "空表 sale 计数应为 0");
        assertEquals(0L, counts.get("asset.material"), "空表 material 计数应为 0");
    }

    private void seedTables() {
        for (String table : TABLE_NAMES) {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table + " (id BIGINT PRIMARY KEY)");
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }
}
