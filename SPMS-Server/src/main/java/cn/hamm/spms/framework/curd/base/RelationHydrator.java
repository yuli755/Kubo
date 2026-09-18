package cn.hamm.spms.framework.curd.base;

import cn.hamm.spms.core.ReflectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <h1>关系对象图的读写桥接（J3）</h1>
 *
 * <p>MyBatis-Plus 只映射列：{@code material} 这样的关系字段在实体上标了
 * {@code @TableField(exist = false)}，而引擎的内置语句（{@code selectById}/{@code selectList}/
 * {@code selectPage}）不会 join 关联表。于是\"查到了但对象图是空的\"这件事必须有人在引擎出口补上 ——
 * 本类就是这个补口，两边各一条路径：</p>
 *
 * <ul>
 *   <li><b>读（回填）</b>：多对一按外键 ID 批量取（一个关系一次查询，不是一行一次）；多对多先按
 *       {@code <仓储>.select<字段>Pairs} 取\"属主 → 目标\"ID 对，再批量取目标实体，最后回填集合。
 *       只在字段为 {@code null} 时回填：调用方自己塞进来的值不被覆盖。</li>
 *   <li><b>写（同步）</b>：多对多的中间表由本类负责 —— 集合非 {@code null} 时按\"这份集合就是期望值\"
 *       {@code delete + insert}（与 JPA 时代把集合设到托管实体上再 flush 的效果一致）；
 *       集合为 {@code null} 时<b>一行不动</b>（局部更新不带集合 ≠ 清空授权）。</li>
 * </ul>
 *
 * <p><b>为什么住框架层而不是某个业务域</b>：它是引擎出口的通用能力，对所有实体生效；
 * 目标实体的仓储按类型在运行时解析（{@link ICurdRepository} 的 Mapper Bean），
 * 所以这里<b>没有一个业务域类型的编译期依赖</b>（分层门禁 L2 的硬约束）。</p>
 *
 * <p>单测里 {@code new XxxService()} 不经容器：依赖缺席时本类不参与，回填跳过 —— 与
 * {@code BaseService} 的 SPI 注入同一口径。</p>
 *
 * @author Arena agent
 */
@Component
public class RelationHydrator {
    private static final Logger log = LoggerFactory.getLogger(RelationHydrator.class);

    /**
     * 多对多中间表语句的三个约定后缀（写在属主仓储的 XML 里）
     */
    private static final String PAIRS_STATEMENT = "Pairs";
    private static final String DELETE_STATEMENT = "ByOwnerId";
    private static final String INSERT_STATEMENT = "ByOwnerIds";

    private final EntityRepositories entityRepositories;
    private final ObjectProvider<SqlSessionTemplate> sqlSessionTemplateProvider;

    public RelationHydrator(EntityRepositories entityRepositories,
                            ObjectProvider<SqlSessionTemplate> sqlSessionTemplateProvider) {
        this.entityRepositories = entityRepositories;
        this.sqlSessionTemplateProvider = sqlSessionTemplateProvider;
    }

    /**
     * 回填一批实体的关系对象图
     *
     * @param entities       实体（空集合直接返回）
     * @param withManyToMany 是否回填多对多集合（列表页可以关掉：那是\"每个字段一次查询\"的代价）
     */
    public void hydrate(@Nullable Collection<? extends CurdEntity<?>> entities, boolean withManyToMany) {
        if (Objects.isNull(entities) || entities.isEmpty()) {
            return;
        }
        List<CurdEntity<?>> list = new ArrayList<>(entities.size());
        for (CurdEntity<?> entity : entities) {
            if (Objects.nonNull(entity)) {
                list.add(entity);
            }
        }
        if (list.isEmpty()) {
            return;
        }
        hydrateManyToOne(list);
        if (withManyToMany) {
            hydrateManyToMany(list);
        }
    }

    /**
     * 回填单个实体的关系对象图（单条查询总是连多对多一起回填）
     *
     * @param entity 实体
     */
    public void hydrate(@Nullable CurdEntity<?> entity) {
        if (Objects.isNull(entity)) {
            return;
        }
        hydrate(List.of(entity), true);
    }

    /**
     * 多对一：按外键 ID 批量取目标实体
     *
     * @param entities 实体
     */
    private void hydrateManyToOne(@NotNull List<CurdEntity<?>> entities) {
        Class<?> entityClass = entities.get(0).getClass();
        for (Field relation : RelationMapping.manyToOneFields(entityClass)) {
            Field foreignKey = RelationMapping.foreignKeyField(entityClass, relation.getName());
            if (Objects.isNull(foreignKey)) {
                continue;
            }
            Map<Long, List<CurdEntity<?>>> waiting = new HashMap<>();
            for (CurdEntity<?> entity : entities) {
                if (Objects.nonNull(ReflectUtil.getFieldValue(entity, relation))) {
                    // 调用方已经给了对象：不回填（可能只是 {id} 的壳，但覆盖它等于改调用方的输入）
                    continue;
                }
                Object value = ReflectUtil.getFieldValue(entity, foreignKey);
                if (!(value instanceof Number number) || number.longValue() <= 0L) {
                    continue;
                }
                waiting.computeIfAbsent(number.longValue(), key -> new ArrayList<>()).add(entity);
            }
            if (waiting.isEmpty()) {
                continue;
            }
            Map<Long, Object> loaded = batchLoad(relation.getType(), waiting.keySet());
            waiting.forEach((id, targets) -> {
                Object target = loaded.get(id);
                if (Objects.isNull(target)) {
                    return;
                }
                targets.forEach(entity -> ReflectUtil.setFieldValue(entity, relation, target));
            });
        }
    }

    /**
     * 多对多：属主 ID → 目标 ID（一次），目标实体批量取（一次），再回填集合
     *
     * @param entities 实体
     */
    private void hydrateManyToMany(@NotNull List<CurdEntity<?>> entities) {
        Class<?> entityClass = entities.get(0).getClass();
        List<Field> fields = RelationMapping.manyToManyFields(entityClass);
        if (fields.isEmpty()) {
            return;
        }
        EntityRepositories.Binding binding = entityRepositories.bindingOf(entityClass);
        SqlSessionTemplate template = sqlSessionTemplate();
        if (Objects.isNull(binding) || Objects.isNull(template)) {
            return;
        }
        List<Long> ownerIds = new ArrayList<>();
        for (CurdEntity<?> entity : entities) {
            if (Objects.nonNull(entity.getId()) && !ownerIds.contains(entity.getId())) {
                ownerIds.add(entity.getId());
            }
        }
        if (ownerIds.isEmpty()) {
            return;
        }
        for (Field field : fields) {
            Class<?> targetClass = RelationMapping.targetEntityOf(field);
            if (Objects.isNull(targetClass)) {
                continue;
            }
            String statement = binding.namespace() + ".select" + capitalize(field.getName()) + PAIRS_STATEMENT;
            List<Map<String, Object>> rows;
            try {
                rows = template.selectList(statement, Map.of("ownerIds", ownerIds));
            }
            catch (RuntimeException ex) {
                // 语句没写（或写错名）不应该把整条读路径打挂：留一条警告，让\"这个集合怎么是空的\"有地方查
                log.warn("多对多回填语句不可用：{}（{}）", statement, ex.getMessage());
                continue;
            }
            Map<Long, List<Long>> ownerToTargets = new HashMap<>();
            Set<Long> targetIds = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                Long ownerId = idOf(row, "ownerId");
                Long targetId = idOf(row, "targetId");
                if (Objects.isNull(ownerId) || Objects.isNull(targetId)) {
                    continue;
                }
                ownerToTargets.computeIfAbsent(ownerId, key -> new ArrayList<>()).add(targetId);
                targetIds.add(targetId);
            }
            if (ownerToTargets.isEmpty()) {
                continue;
            }
            Map<Long, Object> loaded = batchLoad(targetClass, targetIds);
            for (CurdEntity<?> entity : entities) {
                if (Objects.nonNull(ReflectUtil.getFieldValue(entity, field))) {
                    continue;
                }
                List<Long> ids = ownerToTargets.get(entity.getId());
                if (Objects.isNull(ids)) {
                    continue;
                }
                Set<Object> values = new LinkedHashSet<>();
                for (Long id : ids) {
                    Object target = loaded.get(id);
                    if (Objects.nonNull(target)) {
                        values.add(target);
                    }
                }
                ReflectUtil.setFieldValue(entity, field, values);
            }
        }
    }

    /**
     * 写路径：多对多中间表按\"提交的集合就是期望值\"同步
     *
     * @param entity 实体
     * @apiNote 集合为 {@code null} = 这次没提交集合 = 一行不动。集合为空集 = 明确清空全部关联。
     * 需要 {@code entity.getId()} 已经有值（新增走完 insert 之后再同步）。
     */
    public void syncManyToMany(@Nullable CurdEntity<?> entity) {
        if (Objects.isNull(entity) || Objects.isNull(entity.getId())) {
            return;
        }
        List<Field> fields = RelationMapping.manyToManyFields(entity.getClass());
        if (fields.isEmpty()) {
            return;
        }
        EntityRepositories.Binding binding = entityRepositories.bindingOf(entity.getClass());
        SqlSessionTemplate template = sqlSessionTemplate();
        if (Objects.isNull(binding) || Objects.isNull(template)) {
            return;
        }
        for (Field field : fields) {
            Object value = ReflectUtil.getFieldValue(entity, field);
            if (!(value instanceof Collection<?> collection)) {
                // null = 这次没提交这个集合，保持现状
                continue;
            }
            List<Long> ids = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof CurdEntity<?> related && Objects.nonNull(related.getId())) {
                    ids.add(related.getId());
                }
            }
            String deleteId = binding.namespace() + ".delete" + capitalize(field.getName()) + DELETE_STATEMENT;
            String insertId = binding.namespace() + ".insert" + capitalize(field.getName()) + INSERT_STATEMENT;
            try {
                template.delete(deleteId, Map.of("ownerId", entity.getId()));
                if (!ids.isEmpty()) {
                    template.insert(insertId, Map.of("ownerId", entity.getId(), "targetIds", ids));
                }
            }
            catch (RuntimeException ex) {
                // 中间表同步失败必须让人看见：静默失败 = 用户以为改了授权，其实一行没动
                throw new IllegalStateException(
                        "同步关系集合失败：" + entity.getClass().getSimpleName() + "." + field.getName()
                                + "（" + deleteId + " / " + insertId + "）", ex);
            }
        }
    }

    /**
     * 批量取实体（按 ID）
     *
     * @param entityClass 实体类
     * @param ids         ID
     * @return ID → 实体（取不到的 ID 不在表里）
     */
    private @NotNull Map<Long, Object> batchLoad(@NotNull Class<?> entityClass, @NotNull Collection<Long> ids) {
        Map<Long, Object> loaded = new HashMap<>();
        if (ids.isEmpty()) {
            return loaded;
        }
        EntityRepositories.Binding binding = entityRepositories.bindingOf(entityClass);
        if (Objects.isNull(binding)) {
            return loaded;
        }
        List<?> list = binding.repository().selectBatchIds(ids);
        for (Object item : list) {
            if (item instanceof CurdEntity<?> entity && Objects.nonNull(entity.getId())) {
                loaded.put(entity.getId(), item);
            }
        }
        return loaded;
    }

    /**
     * MyBatis 会话模板（容器里没有时返回 {@code null}：单测直接 new 出来的服务）
     *
     * @return 会话模板
     */
    private @Nullable SqlSessionTemplate sqlSessionTemplate() {
        return Objects.isNull(sqlSessionTemplateProvider) ? null : sqlSessionTemplateProvider.getIfAvailable();
    }

    /**
     * 从行里取 ID（别名由 XML 决定；不同 MyBatis 配置对大小写/下划线的处理不同，所以多认几种写法）
     *
     * @param row 行
     * @param key 列别名（驼峰）
     * @return ID
     */
    private @Nullable Long idOf(@NotNull Map<String, Object> row, @NotNull String key) {
        Object value = row.get(key);
        if (Objects.isNull(value)) {
            value = row.get(key.toUpperCase());
        }
        if (Objects.isNull(value)) {
            value = row.get(key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase());
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Objects.isNull(value) ? null : Long.valueOf(value.toString());
    }

    /**
     * 首字母大写（拼语句 ID 用）
     *
     * @param value 字段名
     * @return 首字母大写的字段名
     */
    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
