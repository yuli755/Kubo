package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.exception.ServiceException;
import cn.hamm.spms.core.interfaces.IDictionary;
import cn.hamm.spms.core.interfaces.IFunction;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

/**
 * <h1>枚举字典工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class DictionaryUtil {
    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private DictionaryUtil() {
    }

    /**
     * 查字典
     *
     * @param enumClass 枚举字典类
     * @param key       枚举字典值
     * @param <D>       [泛型] 字典类型
     * @return 查到的字典
     */
    public static <D extends IDictionary> @NotNull D getDictionary(@NotNull Class<D> enumClass, int key) {
        return getDictionary(enumClass, IDictionary::getKey, key);
    }

    /**
     * 查字典
     *
     * @param enumClass 枚举字典类
     * @param function  获取指定值的方法
     * @param value     比较的值
     * @param <D>       字典类型
     * @return 查到的字典
     */
    public static <D extends IDictionary> @NotNull D getDictionary(
            @NotNull Class<D> enumClass, Function<D, Object> function, Object value
    ) {
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(enumItem -> Objects.equals(function.apply(enumItem), value))
                .findFirst()
                .orElseThrow(new ServiceException(
                        "传入的值(" + enumClass.getSimpleName() + "=" + value + ")不在字典可选范围内",
                        getDictionaryList(enumClass))
                );
    }

    /**
     * 获取指定枚举类的 {@code ListMap} 数据
     *
     * @param clazz 枚举类
     * @return 枚举选项列表
     */
    public static <D extends IDictionary> @NotNull List<Map<String, Object>> getDictionaryList(
            @NotNull Class<D> clazz
    ) {
        return getDictionaryList(clazz, IDictionary::getKey, IDictionary::getLabel);
    }

    /**
     * 获取指定枚举类的 {@code ListMap} 数据
     *
     * @param clazz   枚举字典类
     * @param lambdas 需要获取的方法表达式
     * @param <D>     字典类型
     * @return 枚举选项列表
     */
    @SafeVarargs
    public static <D extends IDictionary> @NotNull List<Map<String, Object>> getDictionaryList(
            @NotNull Class<D> clazz, IFunction<D, Object>... lambdas
    ) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        //取出所有枚举类型
        Arrays.stream(clazz.getEnumConstants()).forEach(enumItem -> {
            Map<String, Object> item = new HashMap<>(lambdas.length);
            // 依次取出参数的值
            Arrays.stream(lambdas).forEach(lambda -> {
                try {
                    item.put(StringUtil.uncapitalize(ReflectUtil.getLambdaFunctionName(lambda)), lambda.apply(enumItem));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            });
            mapList.add(item);
        });
        return mapList;
    }
}
