package cn.hamm.spms.common.exception;

import cn.hamm.airpower.core.interfaces.IDictionary;
import cn.hamm.airpower.core.interfaces.IException;
import cn.hamm.spms.common.AppConstant;
import lombok.Getter;
import org.jetbrains.annotations.Contract;

/**
 * <h1>应用自定义异常代码</h1>
 *
 * <p>本仓<b>业务错误码的唯一落点</b>：业务侧抛错统一在此登记枚举值（implements
 * {@link IException}），用 {@code CustomError.XXX.show()} 或
 * {@code throw new ServiceException(CustomError.XXX)} 抛出；code 实际值为
 * {@code BASE_CUSTOM_ERROR + 声明值}（见 {@link AppConstant}）。框架级错误码见<b>同包</b>的
 * {@link Errors}（2026-09-18 起由本仓自持，不再来自 airpower），业务级错误码只此一处。</p>
 *
 * <p>历史上曾另有一份 560 行的 {@code cn.hamm.spms.common.ErrorCode} 枚举（零引用、与本文职责
 * 重复、且 code 空间与 {@link AppConstant#BASE_CUSTOM_ERROR} 偏移冲突），已于 2026-09-09 删除；
 * <b>勿再新建第二套错误码枚举</b>，新增业务码在本枚举追加即可。</p>
 *
 * @author Hamm.cn
 */
@SuppressWarnings("AlibabaEnumConstantsMustHaveComment")
@Getter
public enum CustomError implements IException<CustomError>, IDictionary {
    USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID(1, "用户账号或密码错误"),

    LOGIN_ATTEMPT_TOO_MANY(2, "登录失败次数过多，请稍后再试"),

    EMAIL_SEND_BUSY(101, "发送邮件过于频繁，请稍后再试"),
    SMS_SEND_BUSY(102, "发送短信过于频繁，请稍后再试"),

    REQUEST_TOO_FREQUENT(103, "请求过于频繁，请稍后再试"),
    ;

    private final int code;
    private final String message;

    @Contract(pure = true)
    CustomError(int code, String message) {
        this.code = code + AppConstant.BASE_CUSTOM_ERROR;
        this.message = message;
    }

    /**
     * 获取枚举的 {@code Key}
     *
     * @return {@code Key}
     */
    @Contract(pure = true)
    @Override
    public int getKey() {
        return code;
    }

    /**
     * 获取枚举的描述
     *
     * @return 描述
     */
    @Contract(pure = true)
    @Override
    public String getLabel() {
        return message;
    }
}
