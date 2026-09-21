package cn.hamm.spms.base;


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
import java.lang.reflect.Modifier;

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
    @Column(name = "is_published", nullable = false)
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
}
