package cn.hamm.spms.base;


import cn.hamm.spms.framework.web.annotation.Meta;
import cn.hamm.spms.framework.web.annotation.ReadOnly;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * <h1>应用实体基类</h1>
 *
 * <p>自持实体根模型，承载自增主键、状态位、审计时间戳、发布标识及通用实体操作。
 * 替代旧 {@code CurdEntity} 与 {@code RootModel}。</p>
 *
 * @param <E> 实体类型
 * @author Hamm.cn
 */
@EqualsAndHashCode
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@MappedSuperclass
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@DynamicInsert
@DynamicUpdate
@Slf4j
@SuppressWarnings("unchecked")
public class BaseEntity<E extends BaseEntity<E>> implements Serializable, CurdAction {
    private static final long serialVersionUID = 1L;

    /**
     * 主键字段名
     */
    public static final String STRING_ID = "id";

    /**
     * 创建时间字段名
     */
    public static final String STRING_CREATE_TIME = "createTime";

    /**
     * 主键标记为 {@link Meta}：与旧 {@code airpower.curd.base.CurdEntity#id} 一致，
     * 使 {@link #excludeNotMeta()} 收敛实体时保留 ID —— 菜单树、部门树都靠
     * id/parentId 重建，ID 被清空会导致树构建全废。
     */
    @Meta
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(nullable = false, columnDefinition = "bigint UNSIGNED comment 'ID'")
    @Min(value = 0, message = "ID必须大于{value}")
    @NotNull(groups = {WhenUpdate.class, WhenIdRequired.class}, message = "ID 不能为空")
    private Long id;

    @ReadOnly
    @Column(columnDefinition = "bit(1) default 0 comment '是否禁用'")
    private Boolean isDisabled = false;

    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '创建时间'")
    private Long createTime;

    @ReadOnly
    @Column(columnDefinition = "bigint UNSIGNED default 0 comment '修改时间'")
    private Long updateTime;

    @ReadOnly
    // columnDefinition 必须带 default：同批的 isDisabled/createTime/updateTime 都写了 default 0，
    // 唯独本字段原先只有 nullable=false，Hibernate 便建出「NOT NULL 且无 DB 级默认值」的列。
    // JPA 路径的 INSERT 总会带上该字段（成员初始化 = false）所以看不出问题，但任何裸 SQL
    // 只要没显式列出 is_published 就会撞 "Field 'is_published' doesn't have a default value"
    // —— V2__data_permission_migrations.sql 第 252 行的 INSERT INTO user_tenant 正是如此，
    // 导致「JPA 先建表、Flyway 后迁移」的库（STARTUP.md 规定的本地开发流程）后端起不来。
    // 补上默认值后，JPA 建出的列与 V1/V2 的 `is_published BIT(1) NOT NULL DEFAULT b'0'` 一致。
    @Column(name = "is_published", nullable = false,
            columnDefinition = "bit(1) NOT NULL default 0 comment '是否已发布'")
    private Boolean isPublished = false;

    /**
     * 设置 ID
     *
     * @param id ID
     * @return 实体
     */
    public E setId(Long id) {
        this.id = id;
        return (E) this;
    }

    /**
     * 设置是否禁用
     *
     * @param isDisabled 禁用
     * @return 实体
     */
    public E setIsDisabled(Boolean isDisabled) {
        this.isDisabled = isDisabled;
        return (E) this;
    }

    /**
     * 设置创建时间
     *
     * @param createTime 创建时间
     * @return 实体
     */
    public E setCreateTime(Long createTime) {
        this.createTime = createTime;
        return (E) this;
    }

    /**
     * 设置更新时间
     *
     * @param updateTime 更新时间
     * @return 实体
     */
    public E setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
        return (E) this;
    }

    /**
     * 设置是否已发布
     *
     * @param isPublished 是否已发布
     * @return 实体
     */
    public E setIsPublished(Boolean isPublished) {
        this.isPublished = isPublished;
        return (E) this;
    }

    /**
     * 复制一个只包含 ID 的实体
     *
     * @return 只复制 ID 的实体
     */
    public final E copyOnlyId() {
        try {
            E target = (E) getClass().getConstructor().newInstance();
            return target.setId(getId());
        } catch (Exception e) {
            log.error("复制单 ID 实体失败: {}", e.getMessage(), e);
            throw new RuntimeException("复制单 ID 实体失败: " + e.getMessage(), e);
        }
    }

    /**
     * 复制一个新实体
     *
     * @return 返回实例
     */
    public final E copy() {
        try {
            E target = (E) getClass().getConstructor().newInstance();
            BeanUtils.copyProperties(this, target);
            return target;
        } catch (Exception e) {
            log.error("复制新实体失败: {}", e.getMessage(), e);
            throw new RuntimeException("复制新实体失败: " + e.getMessage(), e);
        }
    }

    /**
     * 排除只读字段（将标记为 @ReadOnly 的属性清空）
     */
    public final void excludeReadOnly() {
        Class<?> current = getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.isAnnotationPresent(ReadOnly.class)) {
                    field.setAccessible(true);
                    try {
                        field.set(this, null);
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
            current = current.getSuperclass();
        }
    }

    /**
     * 收敛<b>嵌套引用</b>的实体为「仅元数据」。
     *
     * <p>移植自旧 {@code airpower.core.RootModel#excludeNotMeta()}，语义按字节码逐条对齐。
     * 注意其真实含义与字面相反：<b>接收者自身保留全部字段</b>，被收敛的是它引用到的
     * 下级实体 —— 调用链为 {@code excludeNotMeta()} → {@code excludeNotMeta(List.of(getClass()))}，
     * 而处理器在 {@code list.contains(this.getClass())} 为真时<b>跳过</b>自身字段收敛，
     * 只沿嵌套模型与集合元素向下递归；递归到下级实体时，下级类不在名单内，
     * 才执行「非 {@link Meta} 字段置空」。</p>
     *
     * <p>用途：{@code afterGetList} 返回列表前切断深层嵌套载荷（如菜单的 parent/children、
     * 用户的 roleList/menuList），列表本身字段不受影响。因此调用方若还要清掉自身某个
     * 非 Meta 字段（如 {@code isPublished}），必须显式再 set null。</p>
     */
    public final void excludeNotMeta() {
        traverseNotMeta(List.of(getClass()));
    }

    /**
     * 按保留名单遍历整条继承链的非 static、非 transient 字段。
     *
     * @param keep 保留自身字段的类名单；名单内的类只向下递归，不收敛自己
     */
    private void traverseNotMeta(List<Class<?>> keep) {
        Class<?> current = getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }
                handleNotMetaField(keep, field);
            }
            current = current.getSuperclass();
        }
    }

    /**
     * 单字段处理：等价于旧 {@code lambda$excludeNotMetaAndDesensitize$3}（desensitize 恒 false）。
     */
    private void handleNotMetaField(List<Class<?>> keep, Field field) {
        field.setAccessible(true);
        Object value;
        try {
            value = field.get(this);
        } catch (IllegalAccessException ignored) {
            return;
        }
        if (value == null) {
            return;
        }
        // 本类不在保留名单：收敛自身字段（对应旧 excludeFieldValueNotMeta）
        if (!keep.isEmpty() && !keep.contains(getClass())) {
            if (value instanceof BaseEntity<?> nested) {
                // 嵌套模型不置空，改为递归收敛（各自以自己为保留名单起点）
                nested.excludeNotMeta();
                return;
            }
            if (!hasMeta(field)) {
                try {
                    field.set(this, null);
                } catch (IllegalAccessException ignored) {
                }
            }
            return;
        }
        // 本类在保留名单：自身字段一律保留，只向下遍历
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof BaseEntity<?> nested) {
                    nested.traverseNotMeta(keep);
                }
            }
            return;
        }
        if (value instanceof BaseEntity<?> nested) {
            nested.traverseNotMeta(keep);
        }
    }

    /**
     * 字段或其 getter 是否标注 {@link Meta}。
     *
     * <p>与旧实现一致：getter 不存在（NoSuchMethodException）时视为「保留」，不置空。</p>
     */
    private boolean hasMeta(Field field) {
        if (field.isAnnotationPresent(Meta.class)) {
            return true;
        }
        String name = field.getName();
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return getClass().getMethod(getter).isAnnotationPresent(Meta.class);
        } catch (NoSuchMethodException ignored) {
            return true;
        }
    }
}
