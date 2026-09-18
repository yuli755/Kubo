package cn.hamm.spms.framework.curd.base;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>实体类 → 写侧仓储 的解析器（J3）</h1>
 *
 * <p>J3 之后有两处必须在运行期"按实体类型找仓储"：关系对象图的回填（多对一取目标实体、多对多取目标集合）
 * 与归属迁移（按资源表名找到实体、再读写那一行）。这两处都不能写死类型 —— 写死就意味着框架层要
 * import 具体业务域（分层门禁 L2 不允许），而逐个分支列举 41 个实体是另一份会过期的清单。</p>
 *
 * <p>解析规则只有一条：仓储 Bean 的 {@code ICurdRepository<E>} 泛型参数等于目标实体类。
 * 命中后连 namespace（仓储接口全限定名，MyBatis XML 的 namespace）一起返回，
 * 因为动态语句的 ID 前缀就是它。解析不到返回 {@code null}：由调用方决定是跳过还是报错，
 * 本类不猜。</p>
 *
 * @author Arena agent
 */
@Component
public class EntityRepositories {
    private static final Logger log = LoggerFactory.getLogger(EntityRepositories.class);

    private final ApplicationContext applicationContext;

    /**
     * 实体类 → 仓储绑定（解析一次）
     */
    private final Map<Class<?>, Binding> bindings = new ConcurrentHashMap<>();

    /**
     * 解析不到的实体类（不重复全量扫描）
     */
    private final Set<Class<?>> unresolved = ConcurrentHashMap.newKeySet();

    public EntityRepositories(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 实体类 → 仓储 + MyBatis namespace
     *
     * @param repository 仓储 Bean
     * @param namespace  仓储接口全限定名（Mapper XML 的 namespace）
     */
    public record Binding(ICurdRepository<?> repository, String namespace) {
    }

    /**
     * 解析实体类对应的仓储
     *
     * @param entityClass 实体类
     * @return 绑定；解析不到（该实体没有写侧仓储）返回 {@code null}
     */
    public @Nullable Binding bindingOf(@NotNull Class<?> entityClass) {
        Binding cached = bindings.get(entityClass);
        if (Objects.nonNull(cached)) {
            return cached;
        }
        if (unresolved.contains(entityClass)) {
            return null;
        }
        for (String beanName : applicationContext.getBeanNamesForType(ICurdRepository.class)) {
            Class<?> type = applicationContext.getType(beanName);
            if (Objects.isNull(type)) {
                continue;
            }
            Class<?> bound = ResolvableType.forClass(type).as(ICurdRepository.class).getGeneric(0).resolve();
            if (!entityClass.equals(bound)) {
                continue;
            }
            Binding binding = new Binding(
                    (ICurdRepository<?>) applicationContext.getBean(beanName), type.getName());
            bindings.put(entityClass, binding);
            return binding;
        }
        unresolved.add(entityClass);
        log.debug("实体 {} 没有对应的 ICurdRepository：按实体类型找仓储的调用方会跳过它", entityClass.getSimpleName());
        return null;
    }
}
