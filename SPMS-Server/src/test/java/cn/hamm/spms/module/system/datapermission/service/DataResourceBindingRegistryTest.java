package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>mappedStatementId → resourceKey 绑定契约</h1>
 *
 * <p>这张表是后续 MyBatis 权限拦截器的第一输入。测试故意不启动 Spring：
 * 绑定是否准确是 MyBatis 配置事实，不应该被数据库、请求上下文或策略缓存的噪声掩盖。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataResourceBindingRegistry · statement 到资源的启动期绑定")
class DataResourceBindingRegistryTest {

    @DataResource(key = "demo.material", alias = "m")
    interface DemoMapper {
    }

    @DataResource(key = "demo.material")
    interface DuplicateMapper {
    }

    interface UnboundMapper {
    }

    @DataResource(key = "")
    interface BlankKeyMapper {
    }

    private static Configuration configuration(String... statementIds) {
        Configuration configuration = new Configuration();
        for (String statementId : statementIds) {
            MappedStatement statement = new MappedStatement.Builder(
                    configuration,
                    statementId,
                    new StaticSqlSource(configuration, "SELECT 1"),
                    SqlCommandType.SELECT
            ).build();
            configuration.addMappedStatement(statement);
        }
        return configuration;
    }

    @Test
    @DisplayName("精确映射 statement ID，并保留资源键与 SQL 别名")
    void mapsStatementsToResource() {
        String countId = DemoMapper.class.getName() + ".countByPermissionFragment";
        String pageId = DemoMapper.class.getName() + ".page";
        DataResourceBindingRegistry registry = DataResourceBindingRegistry.from(
                configuration(countId, pageId), List.of(DemoMapper.class));

        DataResourceBindingRegistry.ResourceBinding binding = registry.bindingFor(countId);
        assertNotNull(binding);
        assertEquals("demo.material", binding.resourceKey());
        assertEquals("m", binding.tableAlias());
        assertEquals(DemoMapper.class, binding.mapperType());
        assertEquals(2, binding.statementIds().size());
        assertEquals(binding, registry.resource("demo.material"));
        assertTrue(registry.mappedStatementIds().contains(pageId));
    }

    @Test
    @DisplayName("不同命名空间不会被前缀误绑")
    void doesNotBindAnotherNamespaceByPrefix() {
        String exact = DemoMapper.class.getName() + ".page";
        String similar = DemoMapper.class.getName() + "Extra.page";
        DataResourceBindingRegistry registry = DataResourceBindingRegistry.from(
                configuration(exact, similar), List.of(DemoMapper.class));

        assertNotNull(registry.bindingFor(exact));
        assertEquals(null, registry.bindingFor(similar));
        assertEquals(1, registry.mappedStatementIds().size());
    }

    @Test
    @DisplayName("Mapper 没有 @DataResource：启动失败，不允许拦截器猜资源")
    void missingAnnotationFailsClosed() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DataResourceBindingRegistry.from(
                        configuration(UnboundMapper.class.getName() + ".page"), List.of(UnboundMapper.class)));

        assertTrue(error.getMessage().contains("@DataResource"), error.getMessage());
    }

    @Test
    @DisplayName("资源键为空：启动失败")
    void blankKeyFailsClosed() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DataResourceBindingRegistry.from(
                        configuration(BlankKeyMapper.class.getName() + ".page"), List.of(BlankKeyMapper.class)));

        assertTrue(error.getMessage().contains("blank"), error.getMessage());
    }

    @Test
    @DisplayName("有注解但没有 XML/注入 statement：启动失败")
    void noStatementsFailsClosed() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DataResourceBindingRegistry.from(configuration(), List.of(DemoMapper.class)));

        assertTrue(error.getMessage().contains("no mapped statements"), error.getMessage());
    }

    @Test
    @DisplayName("同一资源只能有一个 Mapper 命名空间")
    void duplicateResourceKeyFailsClosed() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DataResourceBindingRegistry.from(
                        configuration(
                                DemoMapper.class.getName() + ".page",
                                DuplicateMapper.class.getName() + ".page"),
                        List.of(DemoMapper.class, DuplicateMapper.class)));

        assertTrue(error.getMessage().contains("duplicated @DataResource key"), error.getMessage());
    }
}
