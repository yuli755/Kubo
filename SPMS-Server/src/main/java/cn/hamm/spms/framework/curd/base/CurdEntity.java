package cn.hamm.spms.framework.curd.base;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import cn.hamm.spms.core.RootModel;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.Meta;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.spms.core.interfaces.IEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;


/**
 * <h1>实体根类</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Description("实体")
@Slf4j
@SuppressWarnings("unchecked")
public class CurdEntity<E extends CurdEntity<E>> extends RootModel<E>
        implements Serializable, ICurdAction, IEntity<E> {
    /**
     * 主键字段名
     */
    public static final String STRING_ID = "id";

    /**
     * 创建时间字段名
     */
    public static final String STRING_CREATE_TIME = "createTime";

    @Description("ID")
    // 自增主键 + insert 后把生成值回填到实体（J3 之后主键只此一份声明）
    @TableId(type = IdType.AUTO)
    @Min(value = 0, message = "ID必须大于{value}")
    @NotNull(groups = {WhenUpdate.class, WhenIdRequired.class}, message = "ID 不能为空")
    @Meta
    private Long id;

    @Description("是否禁用")
    @ReadOnly
    private Boolean isDisabled;

    @Description("创建时间")
    @ReadOnly
    private Long createTime;

    @Description("修改时间")
    @ReadOnly
    private Long updateTime;

    /**
     * 设置 ID
     *
     * @param id ID
     * @return 实体
     */
    @Override
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
     * 复制一个只包含 ID 的实体
     *
     * @return 只复制 ID 的实体
     */
    public final @org.jetbrains.annotations.NotNull E copyOnlyId() {
        try {
            E target = (E) getClass().getConstructor().newInstance();
            return target.setId(getId());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException("复制单 ID 实体失败，" + e.getMessage());
        }
    }

    /**
     * 复制一个新实体
     *
     * @return 返回实例
     */
    public final @org.jetbrains.annotations.NotNull E copy() {
        try {
            E target = (E) getClass().getConstructor().newInstance();
            BeanUtils.copyProperties(this, target);
            return target;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException("复制新实体失败，" + e.getMessage());
        }
    }
}
