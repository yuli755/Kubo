package cn.hamm.spms.core.annotation;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.DictionaryUtil;
import cn.hamm.spms.core.interfaces.IDictionary;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记进行字典校验</h1>
 *
 * @author Hamm.cn
 * @apiNote 请注意, 请自行做非空验证, 字典必须实现 {@link IDictionary} 接口
 */
@Constraint(validatedBy = Dictionary.DictionaryValidator.class)
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Documented
public @interface Dictionary {
    /**
     * 错误信息
     */
    String message() default "不允许的枚举字典值";

    /**
     * 使用的枚举类
     *
     * @see IDictionary
     */
    Class<? extends IDictionary> value();

    /**
     * 验证组
     */
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 字典验证实现类
     */
    class DictionaryValidator implements ConstraintValidator<Dictionary, Integer> {
        /**
         * 标记的枚举类
         */
        private Class<? extends IDictionary> enumClazz = null;

        /**
         * 验证
         *
         * @param value   验证的值
         * @param context 验证器会话
         * @return 验证结果
         */
        @Contract("null, _ -> true")
        @Override
        public final boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (null == value) {
                return true;
            }
            try {
                DictionaryUtil.getDictionary(enumClazz, value);
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        /**
         * 初始化
         *
         * @param dictionary 字典类
         */
        @Contract(mutates = "this")
        @Override
        public final void initialize(@NotNull Dictionary dictionary) {
            enumClazz = dictionary.value();
        }
    }

}