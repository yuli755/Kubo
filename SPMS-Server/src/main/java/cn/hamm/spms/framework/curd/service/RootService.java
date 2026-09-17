package cn.hamm.spms.framework.curd.service;

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

import cn.hamm.spms.core.ReflectUtil;
import cn.hamm.spms.core.RootModel;
import cn.hamm.spms.core.annotation.Desensitize;
import cn.hamm.spms.framework.curd.helper.ExportHelper;
import cn.hamm.spms.framework.curd.model.query.QueryExport;
import cn.hamm.spms.framework.redis.RedisHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Objects;

/**
 * <h1>根服务</h1>
 *
 * @author Hamm.cn
 */
@Getter
@Slf4j
@EqualsAndHashCode
public class RootService<E extends RootModel<E>> {
    /**
     * 导出文件帮助类
     */
    @Autowired
    protected ExportHelper exportHelper;

    /**
     * Redis 帮助类
     */
    @Autowired
    protected RedisHelper redisHelper;

    /**
     * 查询导出结果
     *
     * @param queryExport 查询导出模型
     * @return 导出文件地址
     */
    public final String queryExport(@NotNull QueryExport queryExport) {
        return exportHelper.getExportFileUrl(queryExport.getFileCode());
    }

    /**
     * 获取泛型参数
     *
     * @return 类
     */
    public final @NotNull Class<E> getFirstParameterizedTypeClass() {
        //noinspection unchecked
        return (Class<E>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    /**
     * 脱敏
     *
     * @param entity 待脱敏实体
     */
    protected void desensitize(E entity) {
        List<Field> fieldList = ReflectUtil.getFieldList(getFirstParameterizedTypeClass());
        fieldList.forEach(field -> {
            Desensitize desensitize = ReflectUtil.getAnnotation(Desensitize.class, field);
            if (Objects.isNull(desensitize)) {
                // 非脱敏注解标记属性
                return;
            }
            // 脱敏字段
            Object fieldValue = ReflectUtil.getFieldValue(entity, field);
            if (Objects.isNull(fieldValue)) {
                // 值本身是空的
                return;
            }
            if (desensitize.replace() && Objects.equals(desensitize.symbol(), fieldValue.toString())) {
                // 如果是替换 且没有修改内容
                ReflectUtil.setFieldValue(entity, field, null);
            }
            if (!desensitize.replace() && fieldValue.toString().contains(desensitize.symbol())) {
                // 如果值包含脱敏字符
                ReflectUtil.setFieldValue(entity, field, null);
            }
        });
    }
}
