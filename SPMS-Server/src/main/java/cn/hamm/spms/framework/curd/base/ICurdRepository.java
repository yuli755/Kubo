package cn.hamm.spms.framework.curd.base;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名。
 * 2026-09-18 J2【去 JPA】：持久化从 Spring Data JPA 换成 MyBatis-Plus —— 本接口不再继承
 *   `JpaRepository` / `JpaSpecificationExecutor`，改为继承 `BaseMapper`；旧的
 *   `@Lock(PESSIMISTIC_WRITE)` 方法（`getForUpdateById`）换成"语句住在 XML 里"的
 *   `selectByIdForUpdate`（见类注释"行锁为什么在 XML 里"）。
 */

import cn.hamm.spms.core.ReflectUtil;
import cn.hamm.spms.framework.curd.model.query.QueryHelper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.TableField;
import jakarta.persistence.Transient;
import org.apache.ibatis.annotations.Param;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

/**
 * <h1>数据源接口（MyBatis-Plus）</h1>
 *
 * <p>本接口 = {@link BaseMapper}（增删改查的通用语句）＋ 本仓的两件垫片：</p>
 *
 * <ol>
 *   <li>{@link #save(CurdEntity)} / {@link #saveWithNull(CurdEntity)}：把 Spring Data 的
 *       {@code saveAndFlush} 语义补齐 —— 引擎调用点因此一行不用改（id 为空 insert、否则 updateById）；</li>
 *   <li>{@link #selectByIdForUpdate(Long)}：悲观写锁。**它的 SQL 住在各实体的 Mapper XML 里**，
 *       不在本文件里（用户的 SQL 分层规则：SQL 只许住 mapper XML；`FOR UPDATE` 是可执行 SQL，
 *       所以不能写成 {@code wrapper.last("FOR UPDATE")}）。</li>
 * </ol>
 *
 * <h2>行锁为什么在 XML 里</h2>
 * <p>MyBatis-Plus 没有内建的"按 id 加写锁查询"，而写进 Java 的三种替代写法都不可接受：
 * {@code wrapper.last("FOR UPDATE")} 是把 SQL 放进 Java（`pnpm sql:check` 会红）、
 * {@code @Select} 是同一件事的注解版（门禁 B 类直接禁）、自写 {@code SqlInjector} 也是拼 SQL。
 * 于是统一成：**每个写侧仓储有一个同名 XML，里面有一条 {@code selectByIdForUpdate}**。</p>
 *
 * <p>代价是要维护 N 个小 XML；收益是行锁语句能被 DBA 读到、能被 {@code EXPLAIN} 到，
 * 而且 {@code getForUpdate} 在**所有**实体上都可用（不会出现"某个实体第一次调用才抛
 * Invalid bound statement"）。漏一个的后果由 {@code maskContract.spec.ts} 的门禁挡住：
 * "每个 {@code ICurdRepository} 子接口都必须有同名 XML 且含 {@code selectByIdForUpdate}"。</p>
 *
 * @param <E> 实体
 * @author Hamm.cn
 */
public interface ICurdRepository<E extends CurdEntity<E>> extends BaseMapper<E> {
    /**
     * 加写锁（{@code SELECT … FOR UPDATE}）按 ID 查询
     *
     * @param id ID
     * @return 实体（不存在返回 {@code null}）
     * @apiNote SQL 在各实体 Mapper XML 的 {@code selectByIdForUpdate} 里；调用方必须在真实事务内
     * （{@code CurdService#getForUpdate} 会显式校验，锁在 autocommit 下等于没加）。
     */
    E selectByIdForUpdate(@Param("id") Long id);

    /**
     * 保存（id 为空走 insert，否则走 updateById），返回带主键的实体
     *
     * @param entity 实体
     * @return 保存后的实体（主键已回填）
     * @apiNote {@code updateById} 默认**跳过 null 列**，与引擎"null = 不改这一列"的语义一致
     * （引擎在更新前用库里的那条补齐了 null 属性，见 {@code CurdService#saveToDatabase}）。
     */
    default E save(E entity) {
        if (Objects.isNull(entity.getId())) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    /**
     * 保存，并且把 null 也真的写进库（{@code withNull = true} 路径）
     *
     * @param entity 实体
     * @return 保存后的实体
     * @apiNote 逐列 {@code UPDATE … SET}，**跳过 {@code create_time}**：引擎注释写明
     * "更新不允许修改创建时间"，而旧的 JPA 路径其实把 null 合并进去、真写了 NULL ——
     * 按本意修正，这是 J2 唯一一处"行为与换轨前不同"（见 docs §10.8.3）。
     * <p>关系字段（{@code @ManyToOne}）落到外键列；集合关系（{@code @OneToMany}/{@code @ManyToMany}）
     * 不是本表列，跳过。</p>
     */
    default E saveWithNull(E entity) {
        if (Objects.isNull(entity.getId())) {
            insert(entity);
            return entity;
        }
        UpdateWrapper<E> wrapper = new UpdateWrapper<>();
        wrapper.eq(CurdEntity.STRING_ID, entity.getId());
        List<Field> fields = ReflectUtil.getFieldList(entity.getClass());
        boolean any = false;
        for (Field field : fields) {
            String name = field.getName();
            if (CurdEntity.STRING_ID.equals(name) || CurdEntity.STRING_CREATE_TIME.equals(name)) {
                // 主键进 WHERE，创建时间不允许改
                continue;
            }
            if (Objects.nonNull(ReflectUtil.getAnnotation(Transient.class, field))
                    || Objects.nonNull(ReflectUtil.getAnnotation(OneToMany.class, field))
                    || Objects.nonNull(ReflectUtil.getAnnotation(ManyToMany.class, field))) {
                continue;
            }
            TableField tableField = ReflectUtil.getAnnotation(TableField.class, field);
            if (Objects.nonNull(tableField) && !tableField.exist()) {
                continue;
            }
            ManyToOne manyToOne = ReflectUtil.getAnnotation(ManyToOne.class, field);
            if (Objects.nonNull(manyToOne)) {
                // 关系字段落外键列（storage → storage_id）：JPA 时代由 Hibernate 的隐式命名写进去的
                Object related = ReflectUtil.getFieldValue(entity, field);
                Object foreignKey = related instanceof CurdEntity<?> relatedEntity ? relatedEntity.getId() : null;
                wrapper.set(QueryHelper.column(entity.getClass(), name + "Id"), foreignKey);
                any = true;
                continue;
            }
            wrapper.set(QueryHelper.column(entity.getClass(), name), ReflectUtil.getFieldValue(entity, field));
            any = true;
        }
        if (any) {
            update(null, wrapper);
        }
        return entity;
    }

}
