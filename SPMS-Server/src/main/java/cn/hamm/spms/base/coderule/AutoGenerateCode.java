package cn.hamm.spms.base.coderule;

import cn.hamm.spms.base.coderule.CodeRuleField;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>自动生成编码</h1>
 *
 * <p><b>2026-09-18 分层整改（L7）</b>：本注解与它的字段枚举（{@link CodeRuleField}）从
 * {@code common/annotation} 与 {@code module/system/coderule/enums} 一起搬到 {@code base/coderule}（取号契约与字典，实现在 {@code module.system.coderule}）。
 * 理由与 {@code base/spi} 同一条：它被<b>各业务域的实体</b>声明（十几处），
 * 却被 {@code common} 引用 —— 契约留在"用到它的最下层"（base），
 * 兑现它的实现（{@code CodeRuleService} 取号）仍然留在 {@code module.system.coderule}。</p>
 *
 * @author Hamm.cn
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface AutoGenerateCode {
    /**
     * 使用的自定义编码规则枚举项
     */
    CodeRuleField value();
}
