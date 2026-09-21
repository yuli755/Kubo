package cn.hamm.spms.framework.web.annotation;

import cn.hamm.spms.framework.dictionary.IDictionary;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记进行字典校验</h1>
 *
 * <p>替代旧 {@code airpower.core.annotation.Dictionary}。</p>
 *
 * @author Hamm.cn
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
        private Class<? extends IDictionary> enumClazz;

        @Override
        public void initialize(Dictionary dictionary) {
            this.enumClazz = dictionary.value();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (enumClazz == null || !enumClazz.isEnum()) {
                return false;
            }
            for (IDictionary item : enumClazz.getEnumConstants()) {
                if (item.getKey() == value) {
                    return true;
                }
            }
            return false;
        }
    }
}
