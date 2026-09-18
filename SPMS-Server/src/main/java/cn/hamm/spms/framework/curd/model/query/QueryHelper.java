package cn.hamm.spms.framework.curd.model.query;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名。
 * 2026-09-18 J2【去 JPA】：本类从 Criteria/Spring Data 换成 MyBatis-Plus 的 wrapper。
 *   条件语义逐条对齐旧实现，三处刻意的差异写在方法注释上，不写在文档里：
 *     ① 排序（{@link #applySort}）修掉了旧实现"追加排序从未生效"的缺陷；
 *     ② 关系字段（{@code @ManyToOne}）不再 join，直接落到本表 FK 列（J3 起列名来自标量外键字段，
 *        关系对象本身标了 {@code @TableField(exist = false)}）；
 *     ③ 无法表达的条件（嵌套对象只给了非 ID 字段）直接抛，不静默少条件。
 */

import cn.hamm.spms.core.ReflectUtil;
import cn.hamm.spms.core.constant.Constant;
import cn.hamm.spms.framework.curd.annotation.Search;
import cn.hamm.spms.framework.curd.annotation.SearchEmpty;
import cn.hamm.spms.framework.curd.base.CurdEntity;
import cn.hamm.spms.framework.curd.base.RelationMapping;
import cn.hamm.spms.framework.curd.config.CurdConfig;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * <h1>查询帮助类（MyBatis-Plus 版）</h1>
 *
 * <p>J2 之前这一层产出 {@code Criteria} 谓词与 Spring Data 的 {@code Sort}/{@code Pageable}；
 * 现在产出 {@link QueryWrapper} 与本仓的分页对象。**条件语义一字未改**：</p>
 *
 * <ul>
 *   <li>{@code @Search(fullLike = true)} → {@code LIKE '%值%'}，{@code @Search} → {@code LIKE '值%'}，其余 → {@code =}；</li>
 *   <li>{@code isEqual = true}（{@code filter} 路径）→ 一律 {@code =}，忽略 {@code @Search}；</li>
 *   <li>{@code @SearchEmpty} 只决定"空串要不要参与匹配"，没标记就跳过；</li>
 *   <li>{@code @Transient} / {@code @OneToMany} / {@code @ManyToMany} 不参与搜索。</li>
 * </ul>
 *
 * <p>列名解析走 {@link #column(Class, String)}：优先 MP 的 {@code @TableField}（显式声明），
 * 否则按驼峰转下划线兜底 —— 与 MP 自己的列名策略同一口径。
 * {@code pnpm jpa:check} 里有一条判据钉住"显式声明必须等于驼峰转下划线"（写错名字当场红）。</p>
 *
 * @author Hamm.cn
 */
@Service
public class QueryHelper {
    /**
     * 驼峰转下划线（与 MyBatis-Plus 默认的列名策略同口径）
     */
    private static final String CAMEL_PATTERN = "([a-z0-9])([A-Z])";

    @Autowired
    private CurdConfig curdConfig;

    /**
     * 解析字段 → 数据库列名
     *
     * @param entityClass 实体类
     * @param field       字段名（驼峰）
     * @return 列名（下划线）
     * @apiNote 与 MP 的列名策略同源：先看 {@code @TableField}（显式声明优先），否则驼峰转下划线。
     * J3 去掉了 {@code @Column(name)} 这条兜底 —— 实体上不再有那个注解，留着只会是一段永不执行的代码。
     */
    public static @NotNull String column(@NotNull Class<?> entityClass, @NotNull String field) {
        Field declared = ReflectUtil.getField(field, entityClass);
        if (Objects.nonNull(declared)) {
            TableField tableField = ReflectUtil.getAnnotation(TableField.class, declared);
            if (Objects.nonNull(tableField) && StringUtils.hasText(tableField.value())) {
                return tableField.value();
            }
        }
        return camelToUnderline(field);
    }

    private static @NotNull String camelToUnderline(@NotNull String field) {
        return field.replaceAll(CAMEL_PATTERN, "$1_$2").toLowerCase();
    }

    /**
     * 获取非空的分页对象
     *
     * @param page 分页对象
     * @return 分页对象（页码与条数都已补齐）
     */
    public final @NotNull Page requirePageNonNull(@Nullable Page page) {
        page = Objects.requireNonNullElse(page, new Page());
        if (Objects.isNull(page.getPageSize()) || page.getPageSize() <= 0) {
            page.setPageSize(curdConfig.getDefaultPageSize());
        }
        if (Objects.isNull(page.getPageNum()) || page.getPageNum() <= 0) {
            page.setPageNum(1);
        }
        return page;
    }

    /**
     * 获取非空的排序对象
     *
     * @param sort 排序对象
     * @return 排序对象（字段与方向都已补齐）
     */
    public @NotNull Sort requireSortNonNull(@Nullable Sort sort) {
        sort = Objects.requireNonNullElse(sort, new Sort());
        if (!StringUtils.hasText(sort.getField())) {
            sort.setField(curdConfig.getDefaultSortField());
        }
        if (Sort.ASC.equalsIgnoreCase(sort.getDirection())) {
            sort.setDirection(Sort.ASC);
        } else {
            sort.setDirection(Sort.DESC);
        }
        return sort;
    }

    /**
     * 创建 MyBatis-Plus 分页对象
     *
     * @param page 分页对象（页码从 1 开始）
     * @param <E>  实体
     * @return MP 分页对象
     * @apiNote 旧实现走 {@code PageRequest.of(pageNum - 1, size)}，MP 的 {@code current} 从 1 开始 ——
     * 这里**不做 -1**，否则会整体错一页。
     */
    public <E extends CurdEntity<E>> @NotNull com.baomidou.mybatisplus.extension.plugins.pagination.Page<E> createPage(@Nullable Page page) {
        page = requirePageNonNull(page);
        return new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page.getPageNum(), page.getPageSize());
    }

    /**
     * 把排序落到 wrapper 上
     *
     * @param wrapper     查询条件
     * @param entityClass 实体类（{@code Sort} 只带字段名，要靠它解析列名）
     * @param sort        排序对象
     * @param <E>         实体
     * @apiNote <b>与 JPA 时代的行为差异（有意修正）</b>：旧实现写的是
     * {@code result.and(by(desc(...)))}，而 Spring Data 的 {@code Sort} 是不可变对象、
     * {@code and()} 的返回值被丢弃 —— 于是"追加 create_time / id 排序"从未生效，
     * 只在创建时间相同的行上表现为翻页重复或漏行（分页静默错行，最难发现的一类）。
     * 本实现按注释写明的**本意**执行：主排序 → （非创建时间时）create_time DESC → id DESC；
     * 指定 id 排序时后续排序已无意义，只留它一条。
     */
    public <E extends CurdEntity<E>> void applySort(@NotNull QueryWrapper<E> wrapper,
                                                    @NotNull Class<E> entityClass,
                                                    @Nullable Sort sort) {
        sort = requireSortNonNull(sort);
        String field = sort.getField();
        boolean asc = Sort.ASC.equalsIgnoreCase(sort.getDirection());
        wrapper.orderBy(true, asc, column(entityClass, field));
        if (Constant.ID.equals(field)) {
            // 指定了 ID，后续排序已无意义
            return;
        }
        if (!CurdEntity.STRING_CREATE_TIME.equals(field)) {
            wrapper.orderByDesc(column(entityClass, CurdEntity.STRING_CREATE_TIME));
        }
        // 追加 ID 排序，解决创建时间相同的记录排序
        wrapper.orderByDesc(column(entityClass, Constant.ID));
    }

    /**
     * 创建查询条件
     *
     * @param filter  过滤实体
     * @param isEqual 是否强匹配（{@code filter} 路径为 {@code true}，{@code query} 路径为 {@code false}）
     * @param <E>     实体
     * @return 查询条件
     */
    public <E extends CurdEntity<E>> @NotNull QueryWrapper<E> createWrapper(@Nullable E filter, boolean isEqual) {
        QueryWrapper<E> wrapper = new QueryWrapper<>();
        if (Objects.isNull(filter)) {
            return wrapper;
        }
        applySearch(wrapper, filter, isEqual);
        return wrapper;
    }

    /**
     * 递归落条件
     *
     * @param wrapper 查询条件
     * @param search  搜索实体
     * @param isEqual 是否强匹配
     * @param <E>     实体
     */
    public <E extends CurdEntity<E>> void applySearch(@NotNull QueryWrapper<E> wrapper, @NotNull Object search, boolean isEqual) {
        Class<?> searchClass = search.getClass();
        List<Field> fields = ReflectUtil.getFieldList(searchClass);
        fields.forEach(field -> {
            Object fieldValue = ReflectUtil.getFieldValue(search, field);
            if (Objects.isNull(fieldValue)) {
                // 没有传入查询值 跳过
                return;
            }
            SearchEmpty searchEmpty = ReflectUtil.getAnnotation(SearchEmpty.class, field);
            if (!StringUtils.hasText(fieldValue.toString())) {
                if (Objects.isNull(searchEmpty)) {
                    // 没有标记查询空字符串
                    return;
                }
                if (!searchEmpty.value()) {
                    // 标记了 但不查询空字符串
                    return;
                }
            }
            TableField tableField = ReflectUtil.getAnnotation(TableField.class, field);
            if (Objects.nonNull(tableField) && !tableField.exist()) {
                // 非数据库字段（关系对象、查询专用字段如 UserEntity.departmentId）不参与搜索
                return;
            }
            if (Collection.class.isAssignableFrom(field.getType())) {
                // 集合关系（多对多/一对多）不是本表列，wrapper 表达不了
                return;
            }
            if (CurdEntity.class.isAssignableFrom(field.getType())) {
                // 多对一：落到本表外键列（J3 起列名从标量外键字段上取，见 RelationMapping）
                applyManyToOne(wrapper, field, fieldValue, searchClass);
                return;
            }
            String column = column(searchClass, field.getName());
            if (isEqual) {
                // 要求全强匹配
                wrapper.eq(column, fieldValue);
                return;
            }
            Search searchAnnotation = ReflectUtil.getAnnotation(Search.class, field);
            if (Objects.nonNull(searchAnnotation)) {
                // 标记了搜索 则模糊搜索（MP 的 like 自带两侧 %，likeRight 自带右侧 %）
                if (searchAnnotation.fullLike()) {
                    wrapper.like(column, fieldValue);
                    return;
                }
                wrapper.likeRight(column, fieldValue);
                return;
            }
            // 最后兜底还是强匹配
            wrapper.eq(column, fieldValue);
        });
    }

    /**
     * {@code @ManyToOne} 关系落条件
     *
     * <p><b>与 JPA 时代的行为差异（有意）</b>：JPA 走 {@code root.join(field)} 后按关联实体的字段匹配；
     * MP 侧直接落到本表的外键列（{@code storage} → {@code storage_id}），语义等价且少一次 join ——
     * 这也是 Hibernate 对 {@code .get("id")} 的实际做法。</p>
     *
     * <p>关联对象只给了 ID（前端统一这么传）时用 ID 匹配；<b>只给了别的字段</b>（例如"按仓库名搜"）
     * 时 wrapper 表达不了 join，此时**直接抛**：静默少一个条件 = 多返回行，是数据权限语境下
     * 最不能接受的失败方式。</p>
     *
     * @param wrapper     查询条件
     * @param field       关系字段
     * @param fieldValue  关系对象
     * @param searchClass 所属实体类
     * @param <E>         实体
     */
    private <E extends CurdEntity<E>> void applyManyToOne(@NotNull QueryWrapper<E> wrapper,
                                                          @NotNull Field field,
                                                          @NotNull Object fieldValue,
                                                          @NotNull Class<?> searchClass) {
        if (!(fieldValue instanceof CurdEntity<?> related)) {
            throw new IllegalArgumentException("关系字段 " + field.getName() + " 只支持实体类型的查询值");
        }
        if (Objects.nonNull(related.getId())) {
            // storage → storageId → storage_id（Hibernate 隐式外键列名就是这个名字，见 init.sql 的列名口径）
            Field foreignKey = RelationMapping.foreignKeyField(searchClass, field.getName());
            String foreignKeyColumn = Objects.isNull(foreignKey)
                    ? camelToUnderline(field.getName() + "Id")
                    : column(searchClass, foreignKey.getName());
            wrapper.eq(foreignKeyColumn, related.getId());
            return;
        }
        boolean hasOtherField = ReflectUtil.getFieldList(fieldValue.getClass()).stream()
                .anyMatch(nested -> Objects.nonNull(ReflectUtil.getFieldValue(fieldValue, nested)));
        if (!hasOtherField) {
            // 空对象 = 没有条件
            return;
        }
        throw new IllegalArgumentException(String.format(
                "查询条件里的 %s 只给了非 ID 字段，MyBatis-Plus 的 wrapper 无法 join 关联表："
                        + "请改传该对象的 id（推荐），或为这个查询写一条 mapper XML 语句",
                searchClass.getSimpleName() + "." + field.getName()
        ));
    }
}
