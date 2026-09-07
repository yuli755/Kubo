package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h1>MyBatis statement 到数据资源的启动期绑定表</h1>
 *
 * <p>权限拦截器拿到的第一手事实是 {@code mappedStatementId}，不是 Controller 名，也不是实体类名。
 * 如果请求线程再按包名/类名猜资源，遇到明细 Mapper、别名或自定义 XML 时就会静默漂移；
 * 漂移的危险方向是把一个资源的权限条件套到另一张表，或者找不到时放行。</p>
 *
 * <p>本类只在 {@code mapper-read.enabled=true} 的配置块里创建。构造时扫描已经完成的
 * MyBatis {@link Configuration}，要求每一个被 {@code KuboBaseMapper} 扫描到的 Mapper 都有
 * {@link DataResource}，并把它命名空间下的每一条 statement 展开成不可变索引。缺注解、空键、
 * 没有任何 statement 都是启动错误；这些是接线错误，不应等到第一条生产请求才暴露。</p>
 *
 * <p>本阶段不在这里执行 SQL，也不参与 JPA 写路径。它是后续
 * {@code KuboDataPermissionHandler} 的唯一资源解析输入；把"绑定是否存在"从请求线程的
 * SQL 解析中抽出来，才能单独测试并保证 fail-closed 语义。</p>
 *
 * @author Hamm.cn
 */
public final class DataResourceBindingRegistry {

    /**
     * 一个资源绑定到一个 Mapper 命名空间与其主表别名
     *
     * @param resourceKey 资源键
     * @param tableAlias  主表别名；可能为空，表示由资源登记表补齐
     * @param mapperType  Mapper 接口
     * @param statementIds 该 Mapper 命名空间下已注册的 statement ID
     */
    public record ResourceBinding(String resourceKey,
                                  String tableAlias,
                                  Class<?> mapperType,
                                  Set<String> statementIds) {

        public ResourceBinding {
            statementIds = statementIds == null ? Set.of() : Set.copyOf(statementIds);
        }
    }

    private final Map<String, ResourceBinding> byStatementId;

    private final Map<String, ResourceBinding> byResourceKey;

    private DataResourceBindingRegistry(Map<String, ResourceBinding> byStatementId,
                                        Map<String, ResourceBinding> byResourceKey) {
        this.byStatementId = Collections.unmodifiableMap(new LinkedHashMap<>(byStatementId));
        this.byResourceKey = Collections.unmodifiableMap(new LinkedHashMap<>(byResourceKey));
    }

    /**
     * 从容器里的 SqlSessionFactory 构建绑定表
     *
     * @param sqlSessionFactory 已完成 Mapper/XML 注册的工厂
     * @return 绑定表
     */
    public static DataResourceBindingRegistry from(SqlSessionFactory sqlSessionFactory) {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException("SqlSessionFactory is required to build data resource bindings");
        }
        Configuration configuration = sqlSessionFactory.getConfiguration();
        return from(configuration, configuration.getMapperRegistry().getMappers());
    }

    /**
     * 纯配置构建入口，方便不启动 Spring 的契约测试
     *
     * @param configuration MyBatis 配置
     * @param mapperTypes   已由 markerInterface 扫描出的 Mapper 接口
     * @return 绑定表
     */
    static DataResourceBindingRegistry from(Configuration configuration,
                                            Collection<Class<?>> mapperTypes) {
        if (configuration == null) {
            throw new IllegalStateException("MyBatis Configuration is required to build data resource bindings");
        }
        if (mapperTypes == null || mapperTypes.isEmpty()) {
            return new DataResourceBindingRegistry(Map.of(), Map.of());
        }

        Map<String, ResourceBinding> statementBindings = new LinkedHashMap<>();
        Map<String, ResourceBinding> resourceBindings = new LinkedHashMap<>();
        for (Class<?> mapperType : mapperTypes) {
            if (mapperType == null) {
                continue;
            }
            DataResource annotation = mapperType.getAnnotation(DataResource.class);
            if (annotation == null) {
                throw new IllegalStateException("Mapper " + mapperType.getName()
                        + " extends the read-side marker but has no @DataResource binding");
            }
            String resourceKey = annotation.key();
            if (resourceKey == null || resourceKey.isBlank()) {
                throw new IllegalStateException("Mapper " + mapperType.getName()
                        + " declares a blank @DataResource.key");
            }
            ResourceBinding binding = new ResourceBinding(resourceKey, annotation.alias(), mapperType,
                    statementIds(configuration, mapperType));
            if (binding.statementIds().isEmpty()) {
                throw new IllegalStateException("Mapper " + mapperType.getName()
                        + " has @DataResource(" + resourceKey + ") but no mapped statements");
            }
            ResourceBinding previousResource = resourceBindings.putIfAbsent(resourceKey, binding);
            if (previousResource != null) {
                throw new IllegalStateException("duplicated @DataResource key " + resourceKey + ": "
                        + previousResource.mapperType().getName() + " and " + mapperType.getName());
            }
            for (String statementId : binding.statementIds()) {
                ResourceBinding previous = statementBindings.putIfAbsent(statementId, binding);
                if (previous != null) {
                    throw new IllegalStateException("duplicated mapped statement binding " + statementId + ": "
                            + previous.mapperType().getName() + " and " + mapperType.getName());
                }
            }
        }
        return new DataResourceBindingRegistry(statementBindings, resourceBindings);
    }

    private static Set<String> statementIds(Configuration configuration, Class<?> mapperType) {
        String namespace = mapperType.getName() + ".";
        Set<String> ids = new LinkedHashSet<>();
        for (Object candidate : configuration.getMappedStatements()) {
            if (candidate instanceof MappedStatement statement && statement.getId().startsWith(namespace)) {
                ids.add(statement.getId());
            }
        }
        return ids;
    }

    /**
     * 按 MyBatis 的 statement ID 查资源
     *
     * @param mappedStatementId statement ID
     * @return 绑定；未登记返回 null（调用方在 enforce 下必须拒绝）
     */
    public ResourceBinding bindingFor(String mappedStatementId) {
        return mappedStatementId == null ? null : byStatementId.get(mappedStatementId);
    }

    /**
     * 按资源键查绑定
     *
     * @param resourceKey 资源键
     * @return 绑定；未登记返回 null
     */
    public ResourceBinding resource(String resourceKey) {
        return resourceKey == null ? null : byResourceKey.get(resourceKey);
    }

    /**
     * 已绑定的 statement 清单
     *
     * @return 不可变清单
     */
    public Set<String> mappedStatementIds() {
        return byStatementId.keySet();
    }

    /**
     * 已绑定的资源键清单
     *
     * @return 不可变清单
     */
    public Set<String> resourceKeys() {
        return byResourceKey.keySet();
    }

    /**
     * 所有资源绑定，保持扫描顺序供启动日志/诊断使用
     *
     * @return 不可变列表
     */
    public List<ResourceBinding> bindings() {
        return List.copyOf(new ArrayList<>(byResourceKey.values()));
    }
}
